# 010. Payroll period closure

## Status

Accepted. `M11` is built and shipped. This ADR records how a period stops
being editable, and what that transition guarantees. The shape and
coexistence rules for open periods are in ADR-009.

## Context

A payroll period is either **open** or **closed**. Open means payroll
summaries can still be created and edited against it, and its own name,
dates, and frequency may still be edited subject to ADR-009's rules.
Closed means the period is frozen: no new summaries, no edits to existing
summaries, no edits to the period itself.

Closing is the point at which the snapshot chain becomes immutable. The
system's whole `DRAFT` → `VALIDATED` lifecycle is anchored on close, and
reports read their starting point from the most recent closed period.
Without a decision on what close *is*, that lifecycle has no owner.

## Decision

### Close is two-step and atomic

Close is executed in two distinct steps, each with its own guarantee:

- The **pre-flight** runs the coverage checks and returns the results
  without writing anything: the periods that would be closed, the warnings
  the HR Manager would be accepting, and the blockers that would prevent
  the close. It is read-only.
- The **close** runs the same checks again, and if they still hold, builds
  the aggregate, ranking, and percentile snapshots for the monthly period,
  promotes them from `DRAFT` to `VALIDATED`, and flips the `closed` flag on
  the monthly period and on every bi-weekly whose start date falls inside
  the monthly range. All of this happens in one transaction.

**Why two steps instead of one.** The HR Manager settles a period only
after seeing what will happen — which schedules are affected, which
warnings will be accepted, which blockers must be resolved first. A
one-step close would have no place to show that. The pre-flight is not a
UI convenience; it is the checkpoint at which the HR Manager's consent is
informed.

**Why atomic.** If the close could partially succeed — snapshots built but
period not closed, or monthly closed but bi-weeklies not — the snapshot
chain would contain a period whose state contradicts the periods around
it. A report reading from the last closed period would either miss the
partially-closed period or read snapshots from a period it believes is
still open. Atomicity is what keeps "closed" a coherent fact.

### Blockers and warnings

The close's data-quality checks produce two categories of outcomes:

- **Blockers** prevent the close entirely. The HR Manager must resolve
  them (validate a draft payroll summary, register an FX rate, configure
  the reference currency) and re-attempt.
- **Warnings** are things the HR Manager accepts and proceeds through.
  Typically: missing payroll summaries for employees who should have had
  one. The HR Manager sees the warnings in the pre-flight, sees the
  consequences, and explicitly accepts them by confirming the close.

**Why the two categories are distinct.** They are the same distinction the
report engine uses (ADR-004). What blocks a report blocks a close; what a
report warns about, a close warns about. Collapsing them either over-blocks
(rejecting closes the reports would accept) or under-blocks (accepting
closes whose snapshots would be wrong). Keeping the vocabulary identical
across close and report means the HR Manager learns it once.

**Why warnings require explicit acceptance.** A warning means "the
snapshot will be incomplete in a way you might not have intended." If the
system silently proceeded, the HR Manager would only discover the gap in a
later report, at which point the period is already frozen. Explicit
acceptance is what makes the gap a decision rather than an accident.

### Close is the only writer of `VALIDATED`

No other code path promotes a snapshot from `DRAFT` to `VALIDATED`. This
is a hard invariant: the snapshot's "final, immutable" state is set at
exactly one place in the system.

**Why the invariant matters.** `VALIDATED` means "this is the snapshot
reports read from, and it will not change." If a second code path could
write `VALIDATED`, a report could be served stale data after a background
process promoted a snapshot it didn't expect. One writer is the price of
the guarantee.

### A closed period cannot be closed again

Calling close on an already-closed period is a conflict. The operation is
idempotent in its preconditions — the HR Manager cannot accidentally
double-close and produce two sets of `VALIDATED` rows.

**Why the guard matters.** `VALIDATED` is not a state to hold twice for
the same period. A second close would either duplicate rows (reports
wouldn't know which to read) or overwrite (silently changing the frozen
data). Neither is acceptable; the conflict is the only correct outcome.

### Closed periods are immutable

A closed period rejects:

- New payroll summaries.
- Edits to any summary in the period.
- Edits to its own date range, frequency, or **name**.

A period that has any payroll, in any status, cannot be deleted. A period
that has been closed cannot be deleted even if the payroll rows are later
removed — the period's existence is itself a record of the close.

**Why the name is included.** A settled period's name is the identifier
used in reports, snapshots, and any audit log. Renaming a closed period
would silently change the meaning of an already-frozen record. The
immutability is not gratuitous; it protects the identity of data that has
already been reported.

**Why the closed period cannot be deleted even after payroll is removed.**
The closed period is not only a container for payroll; it is the anchor
for the snapshot rows written at close. Deleting it would orphan those
snapshots and silently remove the historical record of the close itself.

### The cascade and the close interact through the bi-weekly range

Closing a monthly period closes the monthly period and **every bi-weekly
period whose start date falls inside the monthly range**, in the same
transaction. This is not optional; a closed monthly period whose nested
bi-weeklies remain open would violate the "no new payroll into a closed
block" invariant — payroll could be recorded against a bi-weekly whose
parent monthly has already been reported as settled.

**Why "start date inside the range", not "fully contained".** The chain
continuation rule (ADR-009) allows a bi-weekly to extend past the monthly
end date. The relationship between a bi-weekly and its monthly is about
the bi-weekly's *start* being inside the monthly range, not its end being
inside it. Using start-of-bi-weekly membership keeps the rule consistent
with how the bi-weekly was created.

### Partial close is not a valid state

The close is one transaction. There is no intermediate state where some
of the affected periods are closed and others are not. If a blocker
appears during the close (a payroll summary was un-validated between the
pre-flight and the close), the whole close is rejected, nothing is written,
and the period remains open. The HR Manager re-attempts the flow.

**Why no partial state.** A partially-closed block — the monthly closed
but a bi-weekly still open, or vice versa — has no defined reporting
semantics. Reports reading from the closed monthly would assume the
bi-weeklies were also settled; a payroll recorded against the still-open
bi-weekly would be silently excluded from the settled block. Atomicity
prevents this class of error from existing.

## Consequences

**Easier:**

- The snapshot chain has a single owner. The close operation is the only
  writer of `VALIDATED` rows, and closed periods are the only periods
  whose snapshots are frozen. Reports can rely on the invariants without
  checking for edge cases.
- Reports and payroll recording stay consistent with the period state: a
  closed period cannot accumulate new payroll, and a report on a closed
  period cannot be surprised by a change.
- The two-step close matches how HR actually settles a period: look at
  what will happen, then decide.
- The warning/blocker split is the same vocabulary the report engine uses.
  The HR Manager sees one model across both flows.
- The close's atomicity means there is no "half-settled" state to reason
  about. A period is open or it is closed; nothing in between.

**Harder:**

- The close transaction is larger than a status update. Building three
  snapshot tables, promoting them, and flipping flags on the monthly and
  every cascaded bi-weekly must be atomic. An exception rolls everything
  back. This is a real cost, but the invariants depend on it.
- Warnings at close require a decision from the HR Manager, not just a
  display. There is no "close anyway" that hides them.
- A blocker discovered during the close (a race with someone un-validating
  a summary between the two steps) rejects the whole operation and forces
  a fresh flow. The HR Manager does not get a partial success; they get a
  clean rejection and must re-attempt.

**What this prevents:**

- **A snapshot written twice.** Close is the only writer of `VALIDATED`
  rows. If close were callable twice, two sets of `VALIDATED` rows would
  exist for the same period and reports would not know which to read.
- **A closed period with payroll drafts.** If close allowed drafts, the
  snapshot would silently exclude them and the report would understate.
- **A partially-closed block.** If close could leave a monthly closed and
  a bi-weekly open, payroll could be recorded against a period whose
  parent has already been reported as settled.
- **Renaming a frozen record.** A closed period's name is fixed. Renaming
  it would change the identity of an already-published record.

## Alternatives considered

**One-step close.** Simpler to build, but gives the HR Manager no chance to
review what will be settled before it is frozen. Rejected.

**Close with drafts, and mark the snapshot as provisional.** A "closed but
`DRAFT`" period would be closed to new payroll but not yet frozen. Every
downstream read would need to check both `closed` and the snapshot
`status`. Too subtle. Rejected.

**Partial close (close the monthly, leave bi-weeklies open).** Would let
payroll continue to be recorded against a sub-period whose parent has
already been reported. Reports would have to model "partially settled" as a
first-class state. Rejected.

**Allow the HR Manager to retry the close from inside the close flow after
a blocker appears mid-flight.** Adds a state machine for a race case that
should be rare. The HR Manager dismisses the close and re-attempts from the
list, where the pre-flight re-runs from a fresh read. Simpler and equally
correct.

## References

- `docs/architecture.md` — snapshot lifecycle, `DRAFT`/`VALIDATED`
  rationale
- `docs/adr/004-enforce-entry-warn-reporting.md` — the blocker/warning
  split reused by the close flow
- `docs/adr/005-snapshot-checkpoints.md` — per-period, per-status snapshots
- `docs/adr/009-payroll-period-shape-and-coexistence.md` — the shape and
  coexistence rules that this ADR's transition operates on
- `docs/adr/021-monthly-cascade-rollback-scope.md` — why the batch endpoint
  is not transactional, and what that means for cascade partial success
- `backend/src/main/resources/db/migration/V202610303__ranking_unique_by_status.sql`
  — the `status` column in the unique key, which prepares for `VALIDATED`
  snapshot rows written by the close flow