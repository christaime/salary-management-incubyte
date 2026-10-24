# Use Cases

> How the HR Manager uses the system. Each use case states the actor, the
> preconditions, the flow, the rules that apply, and the outcome.

The system has one actor: the **HR Manager**. All use cases are initiated by them.

A principle that runs through the whole system: when an action cannot proceed,
the system explains why and offers a way to resolve it. The HR Manager is never
left with a silent failure.

---

## 1. Employee Management

### UC-1.1 — Create an employee

**Preconditions**
- None

**Flow**
1. HR opens the employee list and selects "New employee".
2. A form opens with two sections: identity and first assignment.
3. HR enters identity fields: matricule, name, email, birth date, origin country.
4. HR enters the first assignment fields: start date, country of employment,
   department, role, employment type.
5. HR submits.
6. The system creates the employee and the first assignment together, with
   `inEffect = true`.
7. The HR is returned to the employee list.

**Rules**
- Matricule is required and unique across the organization.
- All identity fields are required.
- The first assignment is required.
- The assignment's start date is required.

**Outcome**
- An `Employee` and an `EmployeePeriodicAssignment` exist, linked.

---

### UC-1.2 — Search the employee list

**Preconditions**
- None

**Flow**
1. HR opens the employee list.
2. The list shows identity columns and in-effect placement columns.
3. HR types into the search bar or sets one or more filters.
4. The list updates.

**Search fields**
- Matricule
- Name
- Email

**Filter fields**
- In-effect country of employment
- In-effect department (by `code` or `name`)
- In-effect role (by `code` or `name`)
- In-effect employment type
- Origin country

**Rules**
- Search and filters combine. All are optional.
- Results are paginated.
- Departments and roles can be searched by `code` or `name`.

**Outcome**
- A filtered, paginated list of employees.

---

### UC-1.3 — View an employee's detail

**Preconditions**
- The employee exists

**Flow**
1. HR selects an employee from the list.
2. The detail page opens.
3. It shows identity and the full list of assignments, ordered by start date.
4. The in-effect assignment is visually marked.

**Rules**
- The detail page is read-only.

**Outcome**
- The HR sees who the employee is and where they have worked.

---

### UC-1.4 — Edit an employee

**Preconditions**
- The employee exists

**Flow**
1. HR opens the employee detail page and selects "Edit".
2. The edit form opens with identity fields, the in-effect assignment as an
   editable row, and a "Create new assignment" button.
3. HR edits identity fields and, optionally, the in-effect assignment row.
4. If the HR clicks "Create new assignment", a new empty row appears below.
5. HR fills in both rows.
6. HR clicks Save.
7. The system validates. If valid, it performs the atomic assignment change. If
   not, it marks the offending fields and explains.

**Rules**
- Identity fields are editable.
- The in-effect assignment row is editable subject to UC-2.3 rules.
- All fields in both rows must be filled before Save succeeds.
- The in-effect assignment's end date must be before the new assignment's start
  date.
- Neither range may overlap with another assignment for the same employee.

**Outcome**
- The employee's identity is updated, and — if a new assignment row was filled —
  the assignment change is applied atomically. The new assignment becomes
  in-effect.

---

### UC-1.5 — Delete an employee

**Preconditions**
- The employee exists and has no payroll, including drafts

**Flow**
1. HR opens the employee detail page and selects "Delete".
2. The system checks for payroll.
3. If the employee has no payroll, the system asks for confirmation.
4. HR confirms.
5. The employee and their assignments are deleted.

**Rules**
- Deletion is rejected if the employee has any payroll summary, in any status.
- The reason is explained.

**Outcome**
- The employee no longer exists.

---

## 2. Assignment Management

Assignments are managed from the employee edit form. There is no separate
assignment area.

### UC-2.1 — Create a new assignment

**Preconditions**
- The employee exists and has an in-effect assignment

**Flow**
1. HR opens the employee edit form.
2. The in-effect assignment row is shown.
3. HR clicks "Create new assignment".
4. A new empty row appears below, with the same columns and a Save button.
5. HR fills in the new assignment's country, department, role, employment type,
   start date, and end date.
6. HR fills in the in-effect assignment's end date.
7. HR clicks Save.
8. The system validates:
    - All fields present in both rows
    - In-effect end date before new start date
    - No overlap with other assignments
    - New start date before new end date
9. If valid, the system applies the change atomically: sets the in-effect
   assignment's end date, marks it not in-effect, and creates the new assignment
   with `inEffect = true`.
10. The new assignment replaces the in-effect row in the form.

**Rules**
- The operation is atomic.
- Every field in both rows is required.
- Any validation failure blocks the save and marks the offending field.

**Outcome**
- The employee has a new in-effect assignment. The previous one is ended and
  remains in the detail page's history.

---

### UC-2.2 — Create a new assignment from the payroll form

**Preconditions**
- HR is recording a payroll summary
- The system reports that the in-effect assignment does not cover the period

**Flow**
1. During payroll recording, the system reports the mismatch and offers two
   fixes: extend the end date, or create a new assignment.
2. HR selects "Create new assignment".
3. The same two-row editing view opens as in UC-2.1.
4. HR fills in both rows and clicks Save.
5. The system validates and applies the atomic operation.
6. Control returns to the payroll form, with the in-effect assignment now
   covering the period.
7. Recording continues.

**Rules**
- Same rules as UC-2.1.
- The new assignment must cover the payroll period being recorded.

**Outcome**
- The assignment is corrected, and payroll recording continues.

---

### UC-2.3 — Edit an assignment

**Preconditions**
- The assignment exists and is in effect

**Flow**
1. HR opens the employee edit form.
2. The in-effect assignment row is shown, with its columns editable.
3. HR edits the fields.
4. HR clicks Save.

**Rules**
- If the assignment has **no** payroll: all fields are editable, subject to the
  overlap rule.
- If the assignment has **payroll**: only the end date is editable, and the new
  end date must still cover every payroll period that references the assignment.
- The start date must be before the end date.
- The end date must not fall inside a payroll period that references the
  assignment.
- Editing the end date — including extending it — does not change the `inEffect`
  flag.

**Outcome**
- The assignment is updated within the allowed constraints.

---

### UC-2.4 — View assignments

**Preconditions**
- The employee exists

**Flow**
1. HR opens the employee detail page.
2. The assignments section lists all assignments, ordered by start date.
3. The in-effect assignment is visually marked.

**Rules**
- The detail page is read-only.

**Outcome**
- The HR sees the employee's placement history.

---

## 3. Payroll Recording

### UC-3.1 — Record a payroll summary

**Preconditions**
- The payroll period exists and is open (`snapshotStatus = NOT_COMPUTED`)
- The previous period of the same frequency is closed

**Flow**
1. HR opens the payroll recording screen.
2. HR selects a payroll period. Only open periods are offered.
3. HR selects an employee, or creates a new one in a modal (UC-1.1).
4. The system resolves the employee's in-effect assignment and renders it.
5. The system checks whether the in-effect assignment's date range covers the
   payroll period.
    - If it does not, recording cannot continue. The system explains why and
      offers two fixes: extend the assignment's end date, or create a new
      assignment (UC-2.2). Recording continues once the assignment covers the
      period.
6. The system checks whether a payroll summary already exists for
   `(employee, period)`.
    - If a `DRAFT` exists, it is loaded for editing.
    - If a `VALIDATED` exists, the HR cannot continue. The system explains why.
7. The currency is set from the assignment's country.
8. HR enters or edits the amounts: base, variable, allowances, employer taxes.
9. The system computes and displays the total.
10. HR saves the summary as `DRAFT`, or saves and validates it.

**Rules**
- The payroll period must fall entirely within the assignment's date range.
- The currency must match the assignment's country currency.
- Components must be non-negative.
- Total must equal the sum of components.
- `(employee, period)` must be unique.
- The period must be open.

**Outcome**
- A payroll summary exists in `DRAFT` or `VALIDATED` status.

**Rationale — why the in-effect assignment is resolved, not chosen.**
Each employee has exactly one in-effect assignment. There is nothing to choose.
Rendering the resolved assignment and offering a fix if it is wrong is simpler
and less error-prone than presenting a list.

**Rationale — why the process cannot continue on a mismatch.**
A payroll summary must reference an assignment that covers its period. If the
in-effect assignment does not, the summary cannot be recorded correctly. The
system does not proceed silently, and it does not proceed with a known error —
it stops, explains, and offers the fix.

---

### UC-3.2 — Edit a draft payroll summary

**Preconditions**
- The summary exists and is in `DRAFT`

**Flow**
1. HR loads the payroll list, filters, and opens the draft.
2. HR edits the amounts.
3. HR saves.

**Rules**
- Only `DRAFT` summaries can be edited.

**Outcome**
- The draft is updated.

---

### UC-3.3 — Delete a draft payroll summary

**Preconditions**
- The summary exists and is in `DRAFT`

**Flow**
1. HR opens the draft and selects "Delete".
2. The system asks for confirmation.
3. HR confirms.
4. The summary is deleted.

**Rules**
- Only `DRAFT` summaries can be deleted.

**Outcome**
- The summary no longer exists.

---

### UC-3.4 — Validate a payroll summary

**Preconditions**
- The summary exists and is in `DRAFT`

**Flow**
1. HR opens the draft and selects "Validate".
2. The system transitions the summary to `VALIDATED`.
3. The summary becomes immutable.

**Rules**
- Validation is one-way.
- A validated summary can no longer be edited or deleted.

**Outcome**
- The summary is `VALIDATED` and immutable.

---

### UC-3.5 — Validate all drafts in a period

**Preconditions**
- The payroll period exists
- At least one draft exists in the period

**Flow**
1. HR opens the period and selects "Validate all drafts".
2. The system attempts to validate each draft.
3. Drafts that pass become `VALIDATED`.
4. Drafts that fail are reported, with reasons.
5. HR sees the result.

**Rules**
- Bulk validation does not fail atomically.
- The system reports each failure.

**Outcome**
- Some or all drafts are validated. Failures are visible.

---

### UC-3.6 — Browse the payroll list

**Preconditions**
- None

**Flow**
1. HR opens the payroll list.
2. The list is initially empty until filters are applied. A date range is
   required.
3. HR sets a date range and optionally additional filters.
4. The list updates.

**Filters**
- Date range (required) — start and end date, not a payroll period
- Employee
- Role (by `code` or `name`)
- Department (by `code` or `name`)
- Country
- Employment type

**Columns**
- Employee
- Payroll period
- Country
- Department
- Role
- Employment type
- Total and currency

**Rules**
- Pagination is available.
- The date range resolves the payroll periods within it.
- Departments and roles can be searched by `code` or `name`.

**Outcome**
- A filtered, paginated list of payroll summaries.

---

## 4. Payroll Period Management

### UC-4.1 — Create a payroll period

**Preconditions**
- The previous period of the same frequency is closed (if one exists)

**Flow**
1. HR opens period management and selects "New period".
2. HR selects a frequency and enters start and end dates.
3. The system generates a name from the dates and frequency.
4. The system checks for overlap with a period of the same frequency.
5. If overlap exists, the system rejects the creation and explains.
6. If no overlap, the system checks whether the previous period of the same
   frequency is closed.
7. If it is not, the system offers to close it first.
8. If the HR declines, the process stops.
9. If the HR accepts, the system opens the previous period's close flow
   (UC-4.4). After it completes, creation resumes.
10. The period is created with `snapshotStatus = NOT_COMPUTED`.

**Rules**
- Frequency is `MONTHLY` or `BI_WEEKLY`.
- Start date must be before end date.
- Monthly periods span 28–31 days; bi-weekly periods span exactly 14 days.
- Name is generated.
- Periods of the same frequency must not overlap.
- The previous period of the same frequency must be closed.

**Outcome**
- A new payroll period exists, ready for payroll recording.

---

### UC-4.2 — List payroll periods

**Preconditions**
- None

**Flow**
1. HR opens period management.
2. The list shows every period, ordered by start date descending, with status.

**Columns**
- Name
- Frequency
- Start date
- End date
- Snapshot status

**Outcome**
- The HR sees every period and its state.

---

### UC-4.3 — Edit or delete a payroll period

**Preconditions**
- The period exists and has no payroll

**Flow**
1. HR opens a period and selects "Edit" or "Delete".
2. If the period has no payroll, the operation proceeds.
3. If it has payroll, the operation is rejected with an explanation.

**Rules**
- Editing and deleting are allowed only when the period has no payroll.
- Editing changes name, dates, or frequency, subject to the creation rules.

**Outcome**
- The period is edited or deleted, or the action is rejected.

---

### UC-4.4 — Close a payroll period

**Preconditions**
- Every payroll summary in the period is `VALIDATED`
- The period's `snapshotStatus` is `NOT_COMPUTED`

**Flow**
1. HR opens the period and selects "Close".
2. The system verifies all summaries are `VALIDATED`. If any are drafts, the
   close is rejected, the drafts are listed, and the reason is explained.
3. The system computes the snapshot: aggregates and rankings.
4. The system stores the snapshot and sets `snapshotStatus` to `DRAFT`.
5. The system presents the snapshot: totals by country, department, and role,
   plus any data-quality issues.
6. HR reviews the snapshot.
7. If there are critical issues, HR must resolve them before validating. The
   system explains what must be fixed.
8. HR validates the snapshot.
9. The system sets `snapshotStatus` to `VALIDATED`. The period is closed.

**Rules**
- Close requires all summaries to be validated.
- The snapshot is generated once.
- Validating the snapshot is the act of closing the period.
- A closed period accepts no further payroll summaries and no edits.

**Outcome**
- The period is closed. Its snapshot is stored and immutable.

---

## 5. Reporting

### UC-5.1 — Generate an aggregate report

**Preconditions**
- None

**Flow**
1. HR opens reporting and selects the aggregate report.
2. HR sets a date range.
3. HR selects zero or more group keys.
4. HR optionally enables component breakdown and statistics.
5. HR submits.
6. The system checks for blockers.
    - If a blocker exists, the report is not produced, and the blocker is
      explained.
7. Otherwise, the system produces the report and shows any warnings.
8. The HR sees the results.

**Rules**
- Group keys: country, department, role, employment type, currency, origin
  country.
- Statistics require country, department, and role as group keys.
- The report is returned in the reference currency.
- Warnings do not block the report.

**Outcome**
- A table of totals, with optional breakdown and statistics, plus any warnings.

---

### UC-5.2 — Generate a ranking report

**Preconditions**
- The payroll period exists

**Flow**
1. HR opens reporting and selects the ranking report.
2. HR selects a payroll period, country, department, and role.
3. HR selects TOP or BOTTOM and a limit (up to 5).
4. HR submits.
5. The system returns the ranked employees.

**Rules**
- The report is period-scoped.
- The limit is at most 5.
- Country, department, and role must be complete.

**Outcome**
- A list of up to 5 employees with names, ranks, and amounts.

---

### UC-5.3 — Generate a comparison report

**Preconditions**
- None

**Flow**
1. HR opens reporting and selects the comparison report.
2. HR sets two date ranges.
3. HR selects one or more group keys.
4. HR submits.
5. The system produces both aggregate results, joins them, and returns the
   comparison.

**Rules**
- Same group keys apply to both periods.
- A group present in only one period is reported with zero on the missing side.
- `deltaPercent` is null when the first period's amount is zero.
- Blockers and warnings apply as in UC-5.1.

**Outcome**
- A table of side-by-side totals with deltas and percentages.

---

### UC-5.4 — Handle blockers and warnings

**Preconditions**
- HR has attempted to generate a report

**Flow**
1. If the report is blocked, the HR sees the blocker, an explanation, and what
   to do about it.
2. If the report is produced with warnings, the HR sees the report and the
   warnings alongside it.

**Rules**
- Blockers prevent the report. Warnings do not.
- Each blocker or warning states the issue and how the system handled it.

**Outcome**
- The HR knows whether the report is reliable, and what to fix if it is not.

---

## 6. Cross-Cutting Behaviors

### Pagination

Every list — employees, payroll, periods, report results — is paginated
server-side. The default page size is 20.

### Data entry is strict, and explains itself

Payroll summaries are validated at save time. When an action cannot proceed,
the system explains why and offers a way to resolve it. The HR Manager is never
left with a silent failure.

### Reporting is lenient

Reports are produced even when the data is incomplete, with warnings. Only
conditions that make the result incorrect block the report.

### No corrections after validation

A validated payroll summary is immutable. A closed period accepts no changes.
Corrections are out of scope.