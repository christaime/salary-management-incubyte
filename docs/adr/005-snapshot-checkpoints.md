# 005. Store snapshot checkpoints when a payroll period closes

## Status

Accepted

## Context

Reports need to convert payroll amounts to a single reference currency (USD) so
figures across countries can be compared and summed.

The straightforward approach is to store historical exchange rates — one rate
per currency per period — and convert at report time. This is correct but has
two costs.

First, it maintains a second piece of historical data that must be accurate for
every period ever reported. The system is not a currency-change system; keeping
rate history is a burden that grows with time.

Second, reports over long ranges must scan and group every payroll summary in
range. For 10,000 employees across multiple years, this is a real cost, and it
recurs on every report.

## Decision

When a payroll period is closed, compute and store a **snapshot** of its
reporting data — cumulative totals by `(country, department, role)`,
with amounts in both original and reference currency, plus the top and bottom
5 earners per combination.

Reports over closed periods read the snapshot. Reports over open periods read
live data.

Snapshots are **cumulative**: each holds totals from the beginning of recorded
payroll through the end of its period. A report over any range starts from the
most recent snapshot that ends before the range, and adds live data for the
remainder. The pattern is the same as computing an account balance: start from
the last known balance, apply subsequent transactions.

Currency rates become current-only. Historical precision is no longer needed,
because closed periods never re-convert.

## Consequences

**Easier:**

- Reports over closed periods are fast. A report over three closed years reads a
  few thousand pre-aggregated rows instead of scanning 10,000 employees'
  payroll summaries across every period.
- The converted values are frozen. If a rate is later corrected, historical
  reports do not change — they reflect what was reported at the time.
- The rate table no longer needs history. It holds one rate per currency pair,
  updated as needed.
- The system can answer min and max questions from the snapshot, because the
  top and bottom earners are stored.

**Harder:**

- Snapshots must be computed at close time. Closing a period is more than a
  status change; it triggers a computation that stores two tables of data.
- Reports over mixed ranges (closed and open periods) combine two sources. The
  algorithm is straightforward, but it must be implemented and tested.
- Some reports cannot be answered from the snapshot — distribution buckets and
  top N beyond 5 are explicitly not supported. This is documented rather than
  hidden.
- The snapshot's totals must match live aggregation at the moment of close. If
  they diverge, one of the two paths has a bug. A consistency test is required.

**What this prevents:**

A recurring cost that would grow with the dataset. Without snapshots, every
historical report would scan and convert the full history. With snapshots, the
cost is paid once at close, and reports thereafter are cheap.