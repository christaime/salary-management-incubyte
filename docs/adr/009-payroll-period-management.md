# 009. Payroll period management: create, close, and the snapshot boundary

## Status

Proposed — not built. `M11` is 0/4. The current system seeds 19 periods
(6 monthly, 13 bi-weekly, 10 closed, 9 open) and provides a read-only
`GET /api/v1/payroll-periods` endpoint. The flows described below are the
intended design, kept as a record of the reasoning for a future milestone.

## Context

The system supports payroll periods: named date ranges with a frequency
(`MONTHLY` or `BI_WEEKLY`) and a `closed` flag. Periods are the anchor for
payroll summaries, snapshot builds, and reports.

**Why two frequencies exist.** A payroll system could operate at a single
frequency and still produce reports. The system supports two because:

1. **Real payroll cadence.** Some employees are paid monthly; others every two
   weeks. Recording at the actual cadence produces accurate ranking amounts —
   a bi-weekly earner's raw paycheck is half a monthly one, and the ranking
   normalizes for that (see ADR-006). Ignoring the cadence would either
   distort rankings or force artificial amounts into the payroll table.
2. **Data-quality checking.** The HR Manager needs to verify that every
   employee with an in-effect assignment has a payroll summary for every
   period that covers their assignment. When bi-weekly periods exist, the
   system can cross-check "this monthly employee has no summary in January"
   and "this bi-weekly employee has a summary in BW1 but not BW2" as separate
   warnings. A monthly-only system would hide the second case entirely.
3. **No duplicate payroll.** If the system only had monthly periods, an HR
   Manager wanting to record two payments for the same employee in the same
   month would either create a second summary with the same `(employee,
   period)` key (rejected by the unique constraint) or alter the amounts of
   the first (mutating history). Bi-weekly periods make multiple payments
   legal at the schema level, because the periods are distinct.

Without a decision on the lifecycle, the snapshot lifecycle cannot be
implemented coherently — the whole point of `status = VALIDATED` on snapshot
rows is that it is written exactly once, at close.

## Decision

### Creation

**Calendar validity is enforced.** A new period's date range must reflect
the actual calendar of the year it is created in:

- `startDate` must be strictly before `endDate`.
- A `MONTHLY` period must span 28–31 days and its start and end dates must
  match a real month in the calendar. (A "February 2026" period starts on
  2026-02-01 and ends on 2026-02-28. A "February 2027" period ends on
  2027-02-28. A leap year's February ends on the 29th.)
- A `BI_WEEKLY` period must span exactly 14 days. It does not need to align
  with a calendar boundary; it only needs to be 14 days long.

**Per-frequency uniqueness.** A new period must not duplicate another period
of the same frequency with the same name **or** the same `(startDate,
endDate)` range. Monthly periods cannot overlap monthly periods; bi-weekly
periods cannot overlap bi-weekly periods. A monthly and a bi-weekly period
may freely overlap — the two cadences are independent.

**Monthly creation can cascade to bi-weekly.** When creating a monthly
period, the HR Manager can opt to also create every bi-weekly period that
falls inside it. A January 2026 monthly period, if cascaded, creates three
bi-weekly periods that cover the month. The cascade is optional: a HR
Manager may create only monthly periods if they want, and add bi-weekly
periods later by hand.

**Bi-weekly creation is independent.** A bi-weekly period can be created on
its own, without a parent monthly period. The calendar validity and the
14-day length rules apply regardless.

**Previous-period-closed requirement.** A new period of frequency F requires
that the previous period of frequency F (by `startDate`) is closed. This
preserves the snapshot chain: a report starting from a closed period's
snapshot assumes every earlier period is also closed. If the previous period
of the same frequency is not closed, the HR Manager is offered the option to
close it first; if they decline, the new period is not created.

### Closing

**Close is atomic.** Closing a period means:

1. The system verifies that no payroll summary in the period is in `DRAFT`
   status. If any are, the close is rejected with a list of the offending
   summaries; the HR Manager must validate or delete them first.
2. The system builds the aggregate, ranking, and percentile snapshots for
   the period.
3. If the build produces **blockers** (`MISSING_REFERENCE_CURRENCY`,
   `MISSING_FX_RATE`, `UNVALIDATED_PAYROLL_SUMMARY`), the close is rejected
   and the period stays open. The HR Manager must resolve the blocker first.
4. If the build produces only **warnings** (`MISSING_PAYROLL_SUMMARY`), the
   warnings are presented to the HR Manager along with the build result. The
   HR Manager decides whether to accept the warnings and proceed.
5. The system writes the snapshot rows with `status = VALIDATED`, replacing
   any existing DRAFT rows for the period.
6. The period's `closed` flag is set to `true`.

Steps 1 through 6 happen in one transaction. If the build blocks, nothing is
written and the period stays open. If the build succeeds and the HR Manager
accepts the warnings, the snapshot is written and the period is closed
together.

**A closed period cannot be closed again.** Calling close on an already-closed
period returns `409 DATA_CONFLICT`. The operation is idempotent in its
preconditions — the HR Manager cannot accidentally double-close and produce
duplicate VALIDATED rows.

**Closed periods are immutable.** A closed period rejects:

- New payroll summaries (`409 PERIOD_CLOSED`).
- Edits to any summary in the period (`409 PERIOD_CLOSED`).
- Edits to its own date range, name, or frequency.

The period row is deleted only if it has no payroll summaries and was never
closed. A period that has any payroll, in any status, cannot be deleted; a
period that has been closed cannot be deleted even if the payroll rows are
later removed.

## Consequences

**Easier:**

- The snapshot chain is preserved. A report over a range that includes any
  closed period has a stable starting point, because every earlier period is
  guaranteed closed.
- The `status = VALIDATED` invariant is enforced at exactly one point — the
  close operation. No other code path writes VALIDATED snapshot rows.
- Reports and payroll recording stay consistent with the period state. A
  closed period cannot accumulate new payroll; a report on a closed period
  cannot be surprised by a change.
- The calendar-validity rules mean the system rejects typos and impossible
  dates at the boundary. A "February 2026" period of 30 days cannot exist.
  This eliminates a class of data-quality issues that would only surface
  later during a report.
- Duplicate names and duplicate date ranges are rejected at creation. The HR
  Manager cannot end up with two "January 2026" periods of the same
  frequency, which would be ambiguous in every list and every report.
- Bi-weekly cascade gives the HR Manager a fast path: one click to set up a
  whole month's worth of bi-weekly payroll periods, without having to
  calculate the calendar day by day.
- The close operation's warning/blocker split is the same policy used at
  report time (ADR-004). A warning means "the snapshot is incomplete but
  usable"; a blocker means "the snapshot would be wrong and cannot be
  committed". The HR Manager sees the same distinction at close as they see
  at report.

**Harder:**

- Period creation is stateful. The HR Manager cannot create an arbitrary
  period; they must work through the calendar in order. This is the point of
  the design, but it is a real constraint.
- Closing requires all summaries to be validated. If the HR Manager has
  drafts they want to abandon, they must delete them explicitly.
- The close transaction is larger than a status update. Building three
  snapshot tables and flipping the flag must be atomic; an exception rolls
  everything back. This is a real complexity cost but the invariants depend
  on it.
- The cascade option creates a compound operation. A monthly creation that
  cascades to bi-weekly is not a single row insert — it is one row plus N
  rows, and the whole operation must either succeed or roll back.
- Warnings at close require a decision from the HR Manager, not just a
  display. The UI must present the warnings clearly and let the HR Manager
  either accept the close or abort it. There is no "close anyway" that
  hides the warnings.

**What this prevents:**

- **A snapshot written twice.** The close operation writes VALIDATED rows
  exactly once. If the period were closeable twice, an HR Manager could
  produce two sets of VALIDATED rows for the same period and reports would
  not know which to read.
- **A closed period with payroll drafts.** If closing allowed drafts, the
  snapshot would silently exclude them and the report would understate.
- **An impossible period.** A "February 2026" with 30 days, a "bi-weekly"
  period with 16 days, a period starting after it ends. All are rejected at
  creation.
- **A period chain break.** If the previous period were not closed before
  the next is created, a report over the range would need to detect the
  first closed period, read from its snapshot, and live-rebuild the open
  periods after it. The mixed-mode complexity is avoided by the chain rule.

## Alternatives considered

**Single frequency (monthly only).** Simpler to build, but loses the two
things frequencies give: accurate rankings at the real cadence, and
fine-grained payroll-recording warnings. A monthly-only system also cannot
represent an employee who is paid every two weeks without aggregating their
paychecks into one monthly amount — which would either distort the ranking
or force the amounts to be entered as a single monthly lump. Rejected.

**Skip the previous-period-closed rule.** Allow arbitrary period creation
and support mixed-mode reporting (snapshot for closed periods, live for open
periods). More flexible but complicates the report path — every report would
need to detect the first closed period before its range, read from that
snapshot, and rebuild from there. Rejected as disproportionate complexity.

**Allow closing with drafts, and mark the snapshot as provisional.** A
"closed but DRAFT" period would be closed to new payroll but not yet frozen.
This could work but complicates every downstream read — a report would need
to check both `closed` and the snapshot status. Rejected as too subtle.

**One-step close (build snapshot, set closed, done).** Works for seeded data
but gives the HR Manager no chance to review the snapshot before it is
frozen. A two-step close (build → present → accept) matches how HR actually
settles a period. The two-step flow is what this ADR describes.

**No cascade on monthly creation.** Simpler to build, but forces the HR
Manager to calculate 3 bi-weekly date ranges by hand for every monthly
period. The cascade gives the fast path without preventing manual
bi-weekly creation. Adopted as optional.

## References

- `docs/architecture.md` — snapshot lifecycle, DRAFT/VALIDATED rationale
- `docs/adr/005-snapshot-checkpoints.md` — per-period, per-status snapshots
- `docs/adr/006-snapshot-ranking-and-percentiles.md` — why rankings
  normalize to monthly rate (which depends on frequency being real)
- `docs/use-cases.md` — UC-4.1 through UC-4.4, marked "not built"
- `docs/planning.md` — why M11 was not built
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` — `closed`
  flag on `payroll_periods`
- `backend/src/main/resources/db/migration/V202610303__ranking_unique_by_status.sql`
  — the `status` column in the unique key, which prepares for VALIDATED
  snapshot rows written by the close flow