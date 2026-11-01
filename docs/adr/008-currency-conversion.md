# 008. Convert currencies via reference_amount and rate

## Status

Accepted

## Context

Payroll amounts are recorded in the assignment country's currency (USD, EUR,
INR, XAF, CAD, GBP, BRL, XOF, CNY). Reports must present totals in a single
reference currency — USD today — so amounts across countries can be compared
and summed.

A currency rate row can be expressed two ways:

1. **One unit of reference = rate units of foreign.** `(1 USD = 583.51 XAF)`
2. **N units of reference = M units of foreign.** `(100 JPY = 0.65 USD)`

Approach 1 is simpler for the code that consumes the rate. Approach 2 is the
convention some FX feeds use (JPY, for example, is often quoted per 100).

The design needs to pick a convention and make it explicit, because a wrong
direction silently corrupts every report.

## Decision

Store rates with **two numeric columns**: `reference_amount` and `rate`.

A row reads as the sentence `reference_amount reference_currency = rate
currency`. Today every seeded row carries `reference_amount = 1`, so each row
reads "1 USD = `rate` `currency`". The `reference_amount` column exists so
that a rate source that quotes per 100 or per 1000 does not require a schema
change — a row like `(100, 'JPY', 0.65, 'USD')` reads "100 JPY = 0.65 USD"
and lives in the same table.

Conversion uses both columns:

```
amount_in_reference  = amount_in_foreign × reference_amount / rate
amount_in_currency   = amount_in_reference × rate / reference_amount
```

Two invariants are enforced by database triggers:

- **Self-pairs.** A row where `currency = reference_currency` must have
  `reference_amount = 1` and `rate = 1`. It represents X-to-X and must be the
  identity.
- **Single reference currency.** All rows in the table must share the same
  `reference_currency`. Changing the reference currency requires deleting the
  table and re-inserting with the new one.

## Consequences

**Easier:**

- Rate source changes are a data change, not a schema change. A quote in per-100
  units can be stored as-is.
- The formula is symmetric. To convert foreign to reference, multiply by
  `reference_amount` and divide by `rate`. To convert reference to foreign,
  invert.
- The conversion is one function. Both directions live in one place, and both
  are documented in the schema.
- Today, with `reference_amount = 1` everywhere, the formula reduces to
  `amount / rate` for foreign-to-reference and `amount × rate` for
  reference-to-foreign. This is the form used in most of the code.

**Harder:**

- The reference currency is global to the table, not per rate. A system that
  needs to report in EUR for one user and USD for another would need a
  schema change.
- The rate is current-only. There is no rate history. Closed period snapshots
  freeze their converted amounts at close time, so historical reports do not
  shift. But a report on an open period uses today's rate, not the rate at
  payroll time. This is documented in ADR 005.
- One place in the code — `AggregateReportService` — uses `amount / rate`
  directly rather than `amount × reference_amount / rate`. This is correct
  today, because every seeded row has `reference_amount = 1`. A future seed
  with non-1 reference amounts would require updating that service. A
  consistent helper that applies the general formula would close the gap.

**What this prevents:**

A single-unit convention that would force a schema change when the rate source
changed. The `reference_amount` column makes the quote direction a data
concern, not a schema concern.
