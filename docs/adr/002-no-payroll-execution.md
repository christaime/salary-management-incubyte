# 002. Do not implement payroll execution

## Status

Accepted

## Context

The assessment asks for "salary management software" that lets the HR Manager
"manage the salaries data" and "answer questions about how the org pays people."

In common usage, "salary management system" often means payroll software:
attendance integration, gross-to-net calculation, tax bands, social
contributions, payslip generation, and statutory reporting. A vendor marketing
page for such a product lists all of these.

The question was whether the assessment intends this full scope.

## Decision

Do not implement payroll execution.

The system records payroll amounts. It does not compute them. Tax rules, gross
to net, social contributions, payslips, and statutory reporting are out of
scope.

## Consequences

**Easier:**

- The scope is bounded. Payroll execution is jurisdiction-specific: each country
  has its own tax rules, thresholds, and reporting requirements. Implementing it
  for even one country would dominate the build; implementing it for eight is
  not possible in the time available.
- The domain model is simpler. Amounts are entered, not derived. There are no
  tax tables, no rate bands, no calculation engine.
- Validation is about consistency (do the amounts add up, does the placement
  match), not about legal correctness.

**Harder:**

- The system cannot answer questions about net pay — only recorded gross
  amounts. This is a real limitation and is documented as such.
- A reader who arrives expecting a payroll system will find a data-management
  and analytics system instead. The requirements document states the exclusion
  explicitly, with a rationale, so this is a deliberate distinction rather than
  a misunderstanding.

**Why this is the right scope:**

The brief describes a replacement for a spreadsheet that holds salary data. The
pain is data management and reporting, not payroll calculation. Reading the
brief literally — "manage the salaries data", "answer questions about how the
org pays people" — supports the data-management interpretation and not the
payroll-execution one.