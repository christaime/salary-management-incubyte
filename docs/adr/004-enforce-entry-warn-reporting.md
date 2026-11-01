# 004. Enforce consistency at data entry, warn about gaps at reporting

## Status

Accepted

## Context

The system can detect two kinds of data issue:

1. A payroll summary that contradicts an employee's assignment (wrong employee,
   assignment not in effect, or assignment does not cover the period).
2. A gap in the data — an employee with an in-effect assignment but no payroll
   summary for a period.

The question was whether these should be rejected, warned about, or ignored.

The answer is not the same for both, because the two situations are different.
Furthermore, some reporting conditions make the report *wrong* rather than
*incomplete*, and those must be handled differently from gaps.

## Decision

Apply different policies at entry and at reporting.

**At entry:** a payroll summary that contradicts an assignment is rejected.
The HR Manager is told what to reconcile, and the summary is not saved until
the contradiction is resolved. The system does not accumulate records that
contradict the assignment.

**At reporting:** the system distinguishes between **blockers** and
**warnings**.

A **blocker** means the report would be wrong if produced. The report is not
generated. A **warning** means the report would be incomplete but still
meaningful. The report is produced, with the warning alongside it.

The conditions currently classified this way:

| Condition | Severity | Why |
| :--- | :--- | :--- |
| `MISSING_PAYROLL_SUMMARY` (employee has an in-effect assignment but no summary) | WARNING | The report is a valid sample of what was paid; it is just missing some employees. |
| `UNVALIDATED_PAYROLL_SUMMARY` (summaries still in DRAFT) | BLOCKER | The report's totals would be lower than reality, because drafts don't count. Producing the report would understate payroll. |
| `MISSING_REFERENCE_CURRENCY` (currency_rates is empty) | BLOCKER | Nothing can be converted. The report would be all zeros. |
| `MISSING_FX_RATE` (a currency used in the period has no rate) | BLOCKER | Some rows could not be converted. The total would be wrong. |
| Invalid date range (start > end) | BLOCKER | The request is incoherent. |
| Future period | BLOCKER | No payroll data can exist for a future period; the report would be empty for a non-obvious reason. |
| Duplicate group keys | BLOCKER | The request is malformed. |
| `ORIGIN_COUNTRY` group key | BLOCKER | The dimension is not supported by the snapshot. |
| Statistics with more than one group key | BLOCKER | The statistics shape does not support it. |

## Consequences

**Easier:**

- Entry stays clean. The system does not accumulate records that contradict the
  assignment.
- Reporting stays useful. The HR Manager can generate a report even when some
  employees are missing payroll summaries, with the gap stated explicitly in
  the warnings array.
- The distinction maps to what the user can actually do. At entry, the HR
  Manager is looking at one record and can fix it immediately. At reporting,
  the HR Manager is asking a question and wants the best available answer, not
  a dead end.
- Blockers prevent wrong numbers. A report that omits unvalidated summaries
  would understate payroll; a report that cannot convert a currency would
  show a partial total. Both are worse than no report at all.

**Harder:**

- The system has two policies where a single policy would be simpler. A reader
  must understand why they differ.
- The distinction between blockers and warnings must be designed explicitly.
  Which conditions block and which warn is a decision, not a default. The
  table above is the current state; adding a new condition requires choosing
  a severity.
- Blockers are all-or-nothing at the period level. A single unvalidated
  summary in a period blocks the entire report for that period. The HR Manager
  must validate or delete every draft in the period before the report runs.
  This is deliberate — a partial report would understate payroll — but it is
  a real operational constraint.

**Why this is the right policy:**

Data entry is a moment of authorship. The HR Manager is creating a record and
can be held to a standard. Reporting is a moment of inquiry. The HR Manager is
asking what is true and should receive the answer along with its limitations.

Rejecting a report because one employee has no summary would make the system
unusable. Accepting a payroll summary that contradicts the assignment would make
the data unreliable. Producing a report that omits unvalidated summaries would
make the numbers wrong. The split above handles all three cases correctly.