# 003. Payroll summaries reference an assignment instead of duplicating placement

## Status

Accepted

## Context

A payroll record describes a payment to an employee in a period. It needs to
know where the employee worked and in what role — for reporting by country,
department, and role.

The straightforward design is to store country, department, and role on the
payroll record itself. Each payroll record carries its own copy of the placement.

But the system also tracks employee assignments: the placement of an employee
over time. Assignments are the source of truth for where an employee worked and
when.

If payroll carries its own placement, the two can contradict. A payroll record
could say "Engineering in France" while the assignment for the same period says
"Product in Germany." Validation can catch this, but validation is a check, not
a guarantee: a bug in the validator, a path that bypasses it, or a later
migration that edits data directly all reintroduce the inconsistency.

## Decision

Payroll summaries reference an `EmployeePeriodicAssignment` instead of storing
country, department, and role directly.

Placement is read from the assignment. The payroll record and the assignment
cannot disagree, because there is only one source.

## Consequences

**Easier:**

- Consistency between payroll and placement is structural, not enforced. It is
  impossible to record a payroll that contradicts the assignment.
- Reports that group by country, department, or role join through the
  assignment — which they would do anyway to include employment type.
- Placement data is stored once. Changing a department name updates one row, not
  every payroll record in that department.

**Harder:**

- Reports that read placement must join. At 10,000 employees the join cost is
  negligible, but it is a real join rather than a direct column read.
- A payroll record cannot describe a placement that has no assignment. This is
  intentional — payroll must correspond to a real assignment — but it means the
  HR Manager must create the assignment before recording payroll.

**What this prevents:**

An entire class of data-quality problem. In the spreadsheet, an employee's
department in one row can differ from their department in another, with no way
to tell which is correct. In this system, the assignment is the answer and
payroll follows it.