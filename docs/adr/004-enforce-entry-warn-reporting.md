# 004. Enforce consistency at data entry, warn about gaps at reporting

## Status

Accepted

## Context

The system can detect two kinds of data issue:

1. A payroll summary that contradicts an employee's assignment.
2. A gap in the data — an employee with an active assignment but no payroll
   summary for a period, or a period with partial country coverage.

The question was whether these should be rejected, warned about, or ignored.

The answer is not the same for both, because the two situations are different.

## Decision

Apply different policies at entry and at reporting.

**At entry:** a payroll summary that contradicts an assignment is rejected. The
HR Manager is told what to reconcile. The summary is not saved until the
contradiction is resolved.

**At reporting:** data gaps are surfaced as warnings. The report is produced,
with a notice explaining what is missing and how the system handled it. The
report is not blocked.

In addition, some reporting conditions are **blockers** rather than warnings:
a missing FX rate, an invalid date range, or a future period prevents the report
from being generated, because the report would be wrong rather than incomplete.

## Consequences

**Easier:**

- Entry stays clean. The system does not accumulate records that contradict the
  assignment.
- Reporting stays useful. The HR Manager can generate a report even when the
  data is incomplete, with the gaps stated explicitly.
- The distinction maps to what the user can actually do. At entry, the HR
  Manager is looking at one record and can fix it immediately. At reporting, the
  HR Manager is asking a question and wants the best available answer, not a
  dead end.

**Harder:**

- The system has two policies where a single policy would be simpler. A reader
  must understand why they differ.
- The distinction between blockers and warnings must be designed explicitly.
  Which conditions block and which warn is a decision, not a default.

**Why this is the right policy:**

Data entry is a moment of authorship. The HR Manager is creating a record and
can be held to a standard. Reporting is a moment of inquiry. The HR Manager is
asking what is true and should receive the answer along with its limitations.

Rejecting a report because one employee has no summary would make the system
unusable. Accepting a payroll summary that contradicts the assignment would make
the data unreliable. Neither extreme is right; the split is.