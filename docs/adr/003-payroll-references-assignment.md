# 003. Payroll summaries reference an assignment and freeze its placement

## Status

Accepted

## Context

A payroll record describes a payment to an employee in a period. It needs to
know where the employee worked and in what role — for reporting by country,
department, and role.

The system also tracks employee assignments: the placement of an employee over
time. Assignments are the source of truth for where an employee worked and
when. An employee has exactly one in-effect assignment, and only its `endDate`
and `inEffect` status are mutable once a payroll summary references it.

Two designs were considered:

1. **Payroll references the assignment only.** Placement is always read by
   joining through the assignment. Consistent, but a report that groups by
   country must join through the assignment table for every row.
2. **Payroll references the assignment, and duplicates its placement.**
   The assignment is the source of truth at the moment payroll is recorded.
   The placement is copied into four denormalized columns for read performance.

## Decision

Use design 2.

The payroll summary holds `employeePeriodicAssignmentId` plus four
denormalized columns: `assignmentCountryId`, `assignmentDepartmentId`,
`assignmentRoleId`, `assignmentEmploymentType`.

The denormalization is **not** to defend against assignment changes. The
service already prevents structural changes to an assignment once any payroll
summary references it. The denormalization exists to **avoid the join** with
the assignment table when reading payroll rows for reports and lists.

## Consequences

**Easier:**

- Reports that group by country, department, role, or employment type do not
  join through the assignment table. The four denormalized columns are
  indexed, so the query is a scan on `payroll_summaries` alone.
- The payroll list can filter and sort on placement attributes without an
  extra join.
- Historical reports reflect the placement as it existed when payroll was
  recorded. Because the service prevents structural changes to a referenced
  assignment, the frozen columns are guaranteed to match the assignment.

**Harder:**

- The four denormalized columns duplicate state. If the service ever allowed
  a structural change to a referenced assignment, the payroll rows would
  become stale. This is prevented by the assignment update rules, not by a
  database constraint.
- Payroll rows are wider. At the seed's 70,000 rows, the overhead is a few MB
  — negligible for the read performance gained.
- A reader of the payroll table has two sources for placement: the
  `employeePeriodicAssignmentId` and the four frozen columns. They are
  guaranteed equal at write time; the ID is the source of truth if they ever
  diverge.

**What this prevents:**

The recurring join cost that a reference-only design would incur on every
payroll list page and every aggregate report query. The assignment table is
the source of truth; the payroll table caches the four attributes that
reporting needs.