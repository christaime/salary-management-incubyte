# AI Workflow

> How AI tools were used to build this project — tools, workflow shape,
> verification, rejections, and open questions.

## Philosophy

AI was the primary collaborator for this project. It was not a per-query
assistant sprinkled on top of human-written code; it wrote the code, wrote the
tests, and answered the domain questions. That is a departure from the usual
"AI-assisted" framing and worth stating plainly.

The working rule that came out of this:

- **The AI writes.** Code, tests, ADRs first drafts, error fixes. The author
  describes intent, reviews the output, runs the result, and decides whether
  to keep it.
- **The author verifies.** Every change is read. Most changes are executed
  before being trusted. The verification burden shifted from "write the
  correct code" to "read and judge the correct code."
- **The author owns.** Anything that ends up in the repository is the author's
  responsibility, regardless of who wrote the first draft. A bug that AI
  introduced is still the author's bug.

This is the honest description of the project. It is also the reason the
`docs/adr/` and `docs/architecture.md` files read the way they do: they are
the artifacts where the human reasoning is forced to surface.

## Tools used

| Tool | Role | Why this tool |
| :--- | :--- | :--- |
| DeepSeek (chat) | Bulk of the code, tests, migrations, backend services | Strong at structured reasoning; produced coherent multi-file changes |
| Gemini (chat) | Angular components, when the initial output was unsatisfying | Better at simple, efficient Angular idioms |
| Stich | UI design exploration | Fast visual alternatives to react to, rather than mock up from scratch |
| AI chat (DeepSeek/Gemini) | Domain research, payroll concepts, ranking/comparison techniques | The domain was new; AI was the source of truth for terminology and standard practices |

**No IDE-integrated tools.** There is no Copilot, no Cursor, no Claude Code in
the loop. Every interaction is a chat session. The code is pasted back into
the editor by hand, or by copy-paste from the chat.

This is a different workflow from "the AI is embedded in the editor." The
practical consequences:

- No autocomplete. Every change is a deliberate paste.
- No per-file context. The AI only knows what is pasted into the conversation.
- No test run inside the chat. Every test run is on the local machine.

The consequence is more friction per change, but a clearer boundary per change
— the reviewer sees exactly what the AI produced, because it arrived as a
paste, not as a silent completion.

## Workflow shape

The workflow is **conversational**, not prompt-engineering-heavy. There are no
prepared prompt templates. There is a conversation, iteration on output, and a
decision to keep, modify, or discard.

### Design

1. Describe the intended screen or flow to Stich.
2. Get 2–3 visual variants.
3. Revise against the data the backend can actually produce — the design is
   constrained by the API, not the other way around.
4. Paste the chosen variant's structure into the AI chat with "build this
   Angular component."

Stich is fast at proposing shapes. It is not aware of the seed data, the
report model, or the field names. Every design suggestion was re-checked
against what the backend could return before being built.

### Backend and test code

1. Describe what a service, adapter, or test should do, in the domain
   vocabulary that was established during research.
2. Get the code back.
3. Read it.
4. Run it against the seed data or the local test suite.
5. Iterate: "the query returns X but should return Y", "the error handling
   should map to 409 not 500", etc.

Most of the backend was produced this way. The tests were produced this way
too — the AI wrote them, the author read them, the author ran them.

### Debugging

1. Run the failing command locally.
2. Copy the error log and the relevant source files into the chat.
3. Get a fix back.
4. Read it, apply it, re-run.
5. If the fix is wrong, paste the new error. Repeat.

This is the loop that consumed the most iterations, especially around SQLite
constraint behavior, JPA mapping edge cases, and Cypher-free JPQL queries that
the Criteria API cannot express.

## Where AI got it wrong

These are real cases where the AI's first answer was rejected, corrected, or
deleted. They are the reason the review step exists.

### 1. `@ManyToOne` relations between entities

The first persistence draft for `PayrollSummaryEntity` included `@ManyToOne`
relations to `EmployeeEntity`, `PayrollPeriodEntity`, and
`EmployeePeriodicAssignmentEntity`. The ADR-003 reasoning (reference + frozen
denormalization) requires that most foreign references be plain UUID columns
so that the criteria pattern and the specification layer keep working. The
`@ManyToOne` relations were removed except for the two that are genuinely
needed for list sorting (`employee`, `payrollPeriod`), and those two are
documented as the deliberate exception.

**Why it was rejected:** it would have broken the "no JPA relations" rule, and
it was not necessary. A UUID column plus an explicit load in the adapter is
what the clean-architecture layout is designed for.

### 2. The `SnapshotBuilder` class

An early iteration produced a `SnapshotBuilder` class meant to be the sole
entry point for building aggregates and rankings. In practice, the two paths
needed different coverage-warning handling, different delete-and-insert
transaction boundaries, and different reference-currency checks. The class was
deleted and replaced by two dedicated methods on
`PayrollPeriodSnapshotRepositoryAdapter`:
`buildAndSaveAggregates(period)` and `buildAndSaveRankings(period)`. The
adapter became a mechanism; the report services decide when to call which.

**Why it was rejected:** one class doing two things with subtly different
rules produced bugs that were hard to trace. Splitting by responsibility
matched the actual code better.

### 3. A naive `SnapshotBuilder.build(PayrollPeriod)` with no persistence

An early design had a pure computation method that returned the built
aggregates and rankings without persisting them, expecting the caller to
persist. This was rejected because the report service would then need to know
about the DTO/entity split and the persistence adapters, which broke the
clean-architecture boundary. The snapshot build and persist became one call.

**Why it was rejected:** it leaked persistence details into the application
layer.

### 4. Statistics as `{min, max, average}` on a single line

The first statistics implementation returned a flat `{min, max, average}` per
aggregate line. That is wrong when the range mixes monthly and bi-weekly
summaries: a bi-weekly paycheck is roughly half a monthly one, so averaging
across both distorts the population. The current shape returns separate
monthly and bi-weekly distributions (`StatisticsDto { monthly?, biWeekly? }`),
each with its own min/max/average/total/count.

**Why it was rejected:** averaging a bi-weekly amount with a monthly amount is
mathematically meaningless. The two populations are different.

### 5. Ranking by raw amount

The first ranking implementation sorted by the raw payroll amount. That ranks
monthly earners roughly twice as high as bi-weekly earners with the same
annual salary. The current implementation ranks by **monthly rate** (raw
amount divided by days covered, multiplied by days in month). The reasoning is
in ADR-006.

**Why it was rejected:** the ranking would have systematically favored one
cadence.

### 6. The `cumulative snapshot` model

An early architecture draft described snapshots as cumulative — each period's
snapshot containing totals from the beginning of payroll through the end of
its period. The current implementation stores per-period snapshots and sums
across periods in the report. Cumulative snapshots add write-time complexity
(the snapshot must be recomputed for every subsequent period when an earlier
period changes) without a demonstrated benefit at the current scale.

**Why it was rejected:** more complexity, no clear win. ADR-005 records the
per-period choice.

### 7. Migrations using `total = base + variable + ...` as a CHECK

The first migration used a raw equality check. On SQLite, `NUMERIC(38, 2)` is
stored as `REAL` (IEEE-754), so the sum of four components can fail the check
on floating-point round-off even when the values are correct. The fix was a
`ROUND(..., 2)`-based CHECK (migrations `V202610281` and `V202610301`).

**Why it was rejected:** the equality check would have failed intermittently
under the seed data.

## Verification practices

The verification burden is what makes the AI-driven workflow responsible. The
practices that emerged:

- **Read every change before pasting.** Line by line, not just the diff shape.
  AI-generated code reads fluently and hides subtle bugs behind idiomatic
  style.
- **Run the tests locally after every change.** No change was accepted without
  a green test run or a manual exercise against the seed data.
- **Check the invariant against the ADRs.** If the AI-produced code does
  something that contradicts an ADR, either the code is wrong or the ADR is
  stale — in either case, the mismatch is investigated before proceeding.
- **Trust the log, not the chat.** When a fix is proposed, the local error log
  after applying it is the source of truth, not the AI's claim that the fix
  works.
- **Write the ADR when the decision is not obvious.** The ADRs were largely
  drafted by AI after the reasoning was described, then read and corrected.
  The reasoning is the human contribution; the write-up is the AI's.

## Prompt engineering techniques

The workflow is not prompt-template driven, but a few techniques recur:

- **Paste the relevant files, not the whole repo.** Every query includes the
  specific files involved (the failing test, the entity, the adapter, the
  migration). The AI's answer is only as good as the context it has.
- **State the invariant, not the code.** "The total must equal the sum of
  components, but SQLite stores NUMERIC as REAL" produces a better answer than
  "write a CHECK constraint."
- **Ask for two options before deciding.** When a design is not obviously
  right, ask for two alternatives and pick one. This surfaces trade-offs
  faster than asking for one and critiquing it.
- **Paste the failure, not the guess.** When something does not work, paste
  the actual error, not "it doesn't work." The AI's fix is only as good as
  the diagnosis it can make from the error.
- **Reject and re-ask.** When the first answer is wrong, the second prompt
  describes why the first answer was wrong and what the actual constraint is.
  Iteration converges faster than a perfect first prompt would.

## Time and quality impact

The honest summary:

- **Faster on boilerplate.** Entity scaffolds, DTO mappers, migration scripts,
  and adapter skeletons were produced in minutes rather than hours. The
  majority of the codebase's structural code is AI-written and AI-tested.
- **Faster on tests.** Tests were written by AI alongside the code. The
  author read them and verified they exercised the right thing. Coverage
  would not have been as complete if tests were written separately.
- **Slower on the tricky parts.** The parts that required real reasoning
  (the snapshot lifecycle, the currency conversion direction, the ranking
  normalization) took more iterations, because each iteration needed the
  author to notice what the AI missed and describe it precisely.
- **The bug rate is not zero.** Six of the corrections above were bugs or
  design errors in AI-written code that were caught by reading and running,
  not by the AI noticing. That is the cost of the workflow: the author
  becomes the quality gate, and the quality gate has to be attentive.

The net assessment: the workflow produced a working system with tests,
documentation, and ADRs, in a timeframe that would not have been possible
writing everything by hand. The correctness of the system is a function of how
carefully the author reviewed, not of how good the AI was.

## Where the AI was the source of truth

The payroll domain was new. Terms like "gross to net", "employer taxes",
"bi-weekly cadence", "earner percentile" — every one of those came from
research conversations with AI chat. The ranking algorithms (raw amount vs
monthly rate, percentile interpolation) were suggested by AI and then
verified against the data. The comparison report's shape (deltas, percent
change) was AI-suggested and then documented as excluded.

This means the domain understanding in this project is second-hand. It was
built by iterating with the AI, not by studying the payroll industry directly.
The ADRs and REQUIREMENTS.md are the trace of that iteration: they capture the
decisions as they were made, and they should be read with the understanding
that the underlying domain knowledge is AI-sourced.

That is a real limitation and worth stating plainly. A production payroll
system written this way would need a domain expert to review every decision
before it reached a real HR team.

## What is not in this file

- **No prompt templates.** The workflow is conversational; templates would
  misrepresent it.
- **No log of every prompt.** The conversations were exploratory and are not
  preserved verbatim. `docs/ai/` contains two milestone-level prompts as
  examples, not as a complete record.
- **No claims about "how much" AI contributed.** The honest answer is "most of
  it," but that is imprecise. What matters is that every change was reviewed
  by the author, and the ADRs and architecture document reflect the author's
  understanding, not the AI's.