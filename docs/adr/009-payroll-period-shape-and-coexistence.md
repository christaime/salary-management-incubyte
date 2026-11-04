# 009. Payroll period shape and coexistence

## Status

Accepted. `M11` is built and shipped. This ADR records how a payroll
period is shaped and how periods coexist: calendar validity, uniqueness,
overlap, the bi-weekly chain, cascade, and the edit rules for open
periods. It does not cover the transition to a closed period; that is
ADR-010.

## Context

A payroll period is a named date range with a frequency (`MONTHLY` or
`BI_WEEKLY`) and a `closed` flag. Periods anchor payroll summaries,
snapshot builds, and reports.

Two frequencies exist because:

1. **Real payroll cadence.** Some employees are paid monthly; others every
   two weeks. Recording at the actual cadence produces accurate ranking
   amounts — a bi-weekly earner's raw paycheck is half a monthly one, and
   the ranking normalizes for that (ADR-006).
2. **Data-quality checking.** The HR Manager must verify that every
   employee with an in-effect assignment has a payroll summary for every
   period that covers their assignment. When bi-weekly periods exist, the
   system can cross-check "this monthly employee has no summary in
   January" and "this bi-weekly employee has a summary in BW1 but not BW2"
   as separate warnings.
3. **No duplicate payroll.** Two payments for the same employee in the
   same month would otherwise collide on the `(employee, period)` key or
   force a mutation of history. Distinct bi-weekly periods make multiple
   payments legal at the schema level.

The rules below describe how periods may be shaped and how they may
coexist, independent of whether any of them are closed.

## Decision

### Calendar validity

A payroll period must be a real, usable date range:

- `startDate` is strictly before `endDate`.
- A `MONTHLY` period spans **28 to 31 days inclusive**. It is not required
  to align with a calendar month.
- A `BI_WEEKLY` period spans exactly 14 days.

The purpose of these rules is to reject impossible periods — a start after
its end, a "monthly" of 10 days, a "bi-weekly" of 16 days — at the
boundary. They are not there to enforce a specific calendar convention.

**Why 28 to 31 days instead of a calendar month.** A monthly period is a
monthly payroll *cycle*, not a calendar month. Real HR workflows include
periods that close on the 25th (so pay lands before the end of the month),
4-4-5 cycles, and shortened adjustment periods. Requiring alignment with
the calendar would forbid all of them. The 28-day lower bound is the
shortest real month (February in a non-leap year); the 31-day upper bound
is the longest. Every real calendar month falls inside the range, and
nothing outside it is a plausible monthly payroll cycle.

**Why bi-weekly is exactly 14 days.** A bi-weekly cycle is defined by its
cadence, not by its position relative to a calendar. 14 days is the
definition; there is no range to negotiate. A 13-day or 15-day period is
not a bi-weekly payroll cycle, and calling it one would break the chain
that the next section describes.

### Uniqueness and overlap

Per frequency:

- A new period must not duplicate the name of another period.
- A new period must not overlap another period of the **same** frequency.

A MONTHLY and a BI_WEEKLY period may freely overlap. A bi-weekly period is
conceptually a sub-period of a monthly one, and the two must coexist on
the same dates.

**Why per-frequency, not global.** A monthly and a bi-weekly period that
cover the same dates are not a data error; they are the intended
relationship. Rejecting that overlap would make the cascade (below)
impossible. Overlap only matters within a cadence, where two periods of
the same frequency on the same dates would be ambiguous in every report.

**Why no ordering constraint on creation.** A new period does not depend
on the state of any other period. HR practice creates periods for the year
ahead, months before any of them close. Requiring that the previous period
be closed before the next can be created would force an ordering that
doesn't match the work, and the report engine does not need that ordering
to be enforced — reports read from closed periods and rebuild from open
ones, so a chain of open periods after a closed one is a supported state,
not an edge case.

### Bi-weekly chain continuation

Bi-weekly periods form a **single chain** across time, not a series of
independently-aligned windows. When a monthly period cascades into
bi-weeklies, the bi-weeklies continue the chain that already exists; they
do not restart at the monthly's start date.

Concretely:

- If a bi-weekly period already exists whose start date is before the
  monthly period's start, the first cascade bi-weekly starts exactly 14
  days after that previous bi-weekly's start — the next link in the chain.
- If no bi-weekly exists before the monthly period, the chain starts at
  the monthly period's start date.
- From the first position, bi-weeklies step forward 14 days at a time while
  their start date is inside the monthly range.
- Each bi-weekly spans 14 days from its start date. The last bi-weekly in
  the cascade may extend **past** the monthly period's end date, because
  the monthly range bounds the sequence's starts, not each link's end.

The naming follows the same principle: `BW<n> <Mon> <Year>` where `<n>`
restarts at 1 within each monthly period, and `<Mon> <Year>` are those of
the **monthly** period, not of the bi-weekly's own start. All bi-weeklies
that cascade from "May 2026" read as `BW1 May 2026`, `BW2 May 2026`, even
if one starts on a June date. The grouping reflects the business entity —
the sub-periods of May 2026 — not the arithmetic of each link.

**Why chain continuation.** The alternative is to restart each monthly
cascade at the monthly's own start date. That produces overlap the moment
a previous month's last bi-weekly ends mid-next-month: the new cascade's
first bi-weekly starts on the monthly's start date, overlapping the
previous chain's tail by up to 13 days. The overlap rule would reject it
(making the cascade impossible after the first month), or the rule would
have to be weakened (violating the per-frequency overlap invariant).
Chain continuation is what makes the cadence a real sequence rather than a
series of independently-aligned windows.

### Bi-weekly creation is independent

A bi-weekly period can be created on its own, without a parent monthly
period. The calendar-validity rules apply. The chain-continuation
principle applies only to the cascade — a HR Manager creating a single
bi-weekly is responsible for where it falls. The system still enforces the
overlap rule, so two bi-weeklies cannot be created on top of each other
regardless of how they were created.

**Why bi-weeklies are not strictly subordinate to a monthly.** A bi-weekly
payroll cycle exists for the employees paid on it, not only as a
decomposition of a monthly. Some organizations operate entirely on
bi-weekly cadence and have no monthly period at all. Treating bi-weeklies
as strictly children of a monthly would force them to exist only where a
monthly exists, which is a modelling mistake for those organizations.

### Cascade is optional

When creating a monthly period, the HR Manager can also create the
bi-weekly periods that fall inside it. The cascade is **optional**: a HR
Manager may create only the monthly period and add bi-weekly periods later
by hand.

The cascade is also **explicit**: the HR Manager opts in by asking for it,
sees what will be produced, and can rename each sub-period before
committing. The system does not silently expand a monthly period into
bi-weeklies; the expansion is a decision the HR Manager makes, and its
result is shown to them before it is committed.

The cascade's effect on names is the one described above (per-monthly
index, monthly's month/year). A HR Manager can override each bi-weekly's
name before the operation is committed.

**Why the cascade is optional, not automatic.** Some organizations operate
monthly-only and have no bi-weekly cycle. Auto-cascading would create
bi-weeklies they don't want, then require them to be deleted. Making it
opt-in preserves the monthly-only path as a first-class flow rather than a
cleanup task.

**Why the cascade is explicit, not a side effect.** A monthly period's
creation is a decision about one entity. Cascading creates N additional
entities that exist in reports, payroll recording, and settlement. The HR
Manager must know what the bi-weeklies will be named, how they'll fall
relative to the existing chain, and what their date ranges will be, before
the operation commits. A silent expansion would be a business decision
taken on the HR Manager's behalf.

### Edit rules for open, non-closed periods

An open period with no payroll summaries can be edited fully: name, dates,
frequency.

An open period with payroll summaries can be **renamed**, but its dates
and frequency are frozen. This is a legitimate lifecycle stage, not an
error state, and the system communicates it to the HR Manager as such.

**Why dates and frequency freeze.** Payroll summaries are keyed on the
period, and reports assume the period's date range matches the summaries'
date range. Allowing a reschedule while summaries exist would silently
move already-recorded payroll into a different reporting window. The HR
Manager may genuinely want to move the payroll — but they must do so by
deleting the summaries and recreating them under a new period, so that the
move is explicit and auditable.

**Why the name still changes.** The name is not part of the payroll key. A
typo ("Janury 2026") is a real thing that happens and should be correctable
without deleting payroll. Renaming an open period is safe because reports
read the name from the period row at read time — no summary carries a
denormalised copy.

## Consequences

**Easier:**

- Calendar validity catches typos and impossible dates at the boundary,
  while still allowing legitimate mid-month monthly cycles. "Monthly" and
  "calendar month" are not the same thing, and the system reflects the
  real distinction.
- Duplicate names and duplicate date ranges are rejected at creation. The
  HR Manager cannot end up with two "January 2026" periods, which would be
  ambiguous in every list and every report.
- The bi-weekly chain is coherent across the year. Creating a monthly
  period's cascade produces bi-weeklies that continue the existing cadence,
  not that reset.
- The cascade gives the HR Manager a fast path — one click to set up a
  month's worth of bi-weekly payroll periods — while remaining opt-in and
  reviewable.
- The rename-without-reschedule rule protects already-recorded payroll
  from silent movement while still allowing routine corrections.

**Harder:**

- Period creation is stateful in a subtle way. A new period cannot freely
  overlap another of the same frequency, and a cascade's first bi-weekly's
  start date depends on the chain that already exists. The preview reflects
  the chain, not an independent window.
- A cascade is a compound operation. A monthly creation that cascades to
  bi-weekly is one monthly row plus N bi-weekly rows, and the operation
  must either succeed or be reported honestly as partial. A partial
  success leaves the HR Manager with some bi-weeklies created and some not;
  the system must show that state and let the HR Manager decide next steps.
  The batch endpoint is not transactional by design (see ADR-021).
- Referenced periods can only be renamed. The HR Manager may expect to
  reschedule a period after payroll exists; the system refuses, and the
  editor must reflect this before the HR Manager invests in filling out
  the form.

**What this prevents:**

- **An impossible period.** A "monthly" of 10 days, a "bi-weekly" of 16
  days, a period starting after it ends: rejected at creation.
- **Cadence drift.** Independent bi-weeklies would drift by up to 13 days
  from the previous chain. The chain-continuation rule keeps the sequence
  true across years.
- **Silent movement of recorded payroll.** The dates-and-frequency freeze on
  referenced periods prevents a reschedule from relocating payroll
  summaries into a different reporting window.

## Alternatives considered

**Single frequency (monthly only).** Simpler to build, but loses accurate
rankings at the real cadence and the fine-grained payroll-recording
warnings. Rejected.

**Strict calendar-month alignment for MONTHLY.** Would forbid legitimate
mid-month starts (25th-of-month close, 4-4-5 cycles, adjustment periods)
and gives no protection a span check doesn't already give. Rejected.

**Previous-period-closed gate on creation.** Would force a creation order
that doesn't match HR practice and adds a gate the report engine does not
rely on. Rejected.

**Independent bi-weeklies per month.** Simpler in isolation, but produces
overlap and cadence drift the moment a previous month's chain ends
mid-next-month. Chain continuation is the fix. Rejected.

**No cascade on monthly creation.** Simpler, but forces the HR Manager to
calculate 3 bi-weekly date ranges by hand for every monthly period.
Adopted as optional rather than mandatory.

**Allow edits to dates on a referenced period.** Would move
already-recorded payroll into a different reporting window without
warning. Rejected.

## References

- `docs/architecture.md` — snapshot lifecycle, `DRAFT`/`VALIDATED`
  rationale
- `docs/adr/006-snapshot-ranking-and-percentiles.md` — why rankings
  normalize to monthly rate (which depends on frequency being real)
- `docs/adr/010-payroll-period-closure.md` — how a period transitions to
  frozen
- `docs/adr/021-monthly-cascade-rollback-scope.md` — why the batch endpoint
  is not transactional, and what that means for cascade partial success
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` —
  `payroll_periods` table