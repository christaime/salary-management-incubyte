# 006. Ranking snapshots normalize to monthly rate; percentiles are annualized

## Status

Accepted

## Context

The system must identify the top and bottom earners in a payroll period, and
it must position a given employee within the broader pay distribution.

Payroll exists at two cadences: MONTHLY and BI_WEEKLY. A bi-weekly paycheck's
raw total is roughly half a monthly paycheck's for the same annual salary.
Ranking by raw amount would systematically favor monthly earners, even when
they earn the same annual salary as a bi-weekly earner. The report needs a
normalization that makes the two cadences comparable.

The percentile distribution has a second problem: it must be displayable as
"this salary sits at the Nth percentile of annual compensation." Percentiles
of monthly or bi-weekly amounts require the reader to know the cadence to
interpret the number. An annualized distribution does not.

## Decision

**Rank by monthly rate, not raw amount.** Each payroll summary contributes
`total / daysCovered` to the employee's monthly rate, where `daysCovered` is
`daysInMonth` for a MONTHLY summary and `14` for a BI_WEEKLY summary. The
monthly rate for an employee in the period is:

```
monthly_rate = (SUM(total) / SUM(daysCovered)) × daysInMonth
```

Where `daysInMonth` is the length of the month in which the payroll period
starts. A bi-weekly period from 2026-01-29 to 2026-02-11 is normalized
against January's 31 days, because that is where the period starts.

**Store percentiles annualized.** The 7-point distribution (min, p10, p25,
p50, p75, p90, max) is stored as `monthly_rate × 12`. The report interpolates
the earner's percentile from this annualized distribution and expresses it in
basis points (0–10000).

**Percentile distributions require at least 10 employees.** Below 10, the
percentile boundaries are noise. The builder skips the distribution entirely,
and the report surfaces a null `positionContext` for that partition.

## Consequences

**Easier:**

- A bi-weekly earner and a monthly earner with the same annual salary rank
  identically. This is the desired outcome.
- The percentile badge ("this salary is at the 73rd percentile") is stable
  across cadences, because both the stored distribution and the earner's
  comparison value are annualized.
- The report can show a top-5 earner at "P100, 301k USD" regardless of whether
  their payroll cadence is monthly or bi-weekly.

**Harder:**

- The stored `monthly_rate_amount` differs from the stored `reference_amount`
  by a cadence-dependent factor. A report that displays "this person earns X
  per period" must use `original_amount`, not `monthly_rate_amount`, or
  normalize explicitly.
- The annualization factor of 12 lives in two places: the builder's JPQL
  (`annualizedExpr`) multiplies by 12, and `RankingReportService` multiplies
  the retrieved monthly rate by a literal 12 before comparing to the
  distribution. Both must stay in sync; a shared constant would eliminate the
  risk. Today they are maintained together in the same milestone.
- `daysInMonth` is the length of the month the period **starts** in, not the
  month the period mostly covers. A bi-weekly period that starts on the last
  day of a month is normalized against that month's length, not the next.
  A more precise rule would prorate by actual days in each month, but that
  requires per-day resolution the summary data does not carry. The
  approximation is documented and acceptable for ranking.
- Groups with fewer than 10 members have no percentile distribution. The
  report's `positionContext` is null for those groups, and the per-earner
  percentile badge is null. The UI must handle this without breaking layout.

**What this prevents:**

Cadence bias in the ranking. A full-time bi-weekly earner and a full-time
monthly earner in the same role earn the same annualized amount, but a
raw-amount ranking would place the monthly earner roughly twice as high.
Normalizing to monthly rate removes that bias.
