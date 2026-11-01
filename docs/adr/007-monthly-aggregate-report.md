# 007. Aggregate report operates at monthly granularity

## Status

Accepted

## Context

The system supports two payroll cadences: MONTHLY and BI_WEEKLY. Some
employees are paid monthly; others are paid every two weeks. Both cadences
coexist in the same period range.

The aggregate report needs to answer "how much did we pay, grouped by X" over
a date range. The design must decide whether the report treats a bi-weekly
period as a report anchor in its own right, or rolls it into the containing
month.

Three options were considered:

1. **Both cadences are independent report anchors.** A range that spans three
   months returns 6 monthly + 13 bi-weekly aggregates, all summed. Accurate
   at the row level, but the report's lines mix different populations and
   different time spans.
2. **Only monthly periods are anchors; bi-weekly amounts are rolled into their
   containing month for the primary report, but handled separately for statistics.**
   A range that spans three months returns 3 monthly aggregate snapshot rows.
   However, the aggregate service runs a secondary query against the live
   payroll summaries to compute the true bi-weekly statistics.
3. **Only the frequency the user selects is an anchor.** The user picks
   "monthly" or "bi-weekly", and the report returns only that frequency's
   periods. Requires two report runs to see both cadences.

## Decision

Use option 2. The aggregate report resolves **MONTHLY periods only** for the
primary aggregate snapshot lines. Every bi-weekly payroll summary is
attributed to the monthly period that contains its `start_date`, using a
half-open window:

```
monthly.start_date <= biweekly_period.start_date < monthly.end_date
```

The upper bound is exclusive so a bi-weekly period that starts on the last day
of a month is attributed to the next month, not the current one.

However, if statistics are requested (`includeStatistics=true`), the service
bypasses the aggregate snapshot for that calculation and queries the live
`payroll_summaries` table directly. It runs two separate queries—one for
MONTHLY frequency and one for BI_WEEKLY frequency—to calculate the min, max,
and average for each cadence independently.

## Consequences

**Easier:**

- Primary aggregate lines are comparable across periods. A row for "January
  2026" has the same logical shape as a row for "February 2026".
- The report answers "how much did we pay per month" using the fast monthly
  snapshot.
- Bi-weekly questions (like the spread of a bi-weekly paycheck) are answered
  accurately because the statistics query hits the live payroll table,
  completely ignoring the monthly snapshot for that specific calculation.
- Statistics can safely display separate monthly and bi-weekly distributions
  side-by-side, avoiding the mathematical error of averaging a bi-weekly
  paycheck with a monthly one.

**Harder:**

- The statistics query must scan live `payroll_summaries`. While fast enough at
  70,000 rows, it is inherently slower than reading a snapshot.
- The line's `amount` becomes the sum of the monthly and bi-weekly totals when
  statistics are requested. This replaces the aggregate row's total, which is a
  subtle behavior that the report service must document.
- The attribution rule for the primary snapshot is an approximation. A
  bi-weekly period from 2026-01-29 to 2026-02-11 is attributed entirely to
  January (because it starts in January), even though most of its days are in
  February. A more precise rule would prorate by actual days. This is
  documented and accepted for the primary aggregate granularity.

**What this prevents:**

A report whose rows are not comparable. If the primary lines mixed monthly and
bi-weekly anchors, a line for "January 2026" would contain a full monthly
paycheck plus however many bi-weekly paychecks happened to fall in the month,
making rows incomparable. By restricting the snapshot to monthly anchors and
using live queries for frequency-specific statistics, the report remains
accurate and visually comparable.
