# Use Cases

> How the HR Manager uses the system. Each use case states the actor, the
> preconditions, the flow, the rules that apply, and the outcome.

The system has one actor: the **HR Manager**. All use cases are initiated by them.

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
6. The system creates the employee and the first assignment together.
7. The HR is returned to the employee list, with the new employee visible.

**Rules**
- Matricule is required and must be unique across the organization.
- All identity fields are required.
- The first assignment is required — an employee without one has no placement
  and cannot be used for payroll.
- The assignment's start date is required. Its end date is not set at creation.
- The assignment's start date must be today or earlier. Future-dated assignments
  are not created from this form.

**Outcome**
- An `Employee` and an `EmployeePeriodicAssignment` exist, linked.

**Rationale — why creation requires an assignment.**
The employee record has no meaning in this system without a placement. Payroll
references the assignment, reports group by placement, and the employee list
shows current placement. Creating an employee without an assignment would
produce a record that cannot participate in any of the system's purpose.

---

### UC-1.2 — Search the employee list

**Preconditions**
- None

**Flow**
1. HR opens the employee list.
2. The list shows identity columns and current placement columns.
3. HR types into the search bar or sets one or more filters.
4. The list updates.

**Search fields**
- Matricule
- Name
- Email

**Filter fields**
- Current country of employment
- Current department
- Current role
- Current employment type
- Origin country

**Rules**
- Search and filters combine. All are optional.
- "Current" means the assignment whose date range contains today.
- Results are paginated.

**Outcome**
- A filtered, paginated list of employees with current placement.

**Rationale — why current placement is filterable.**
The HR Manager's most common query is "who works in X?". Resolving the current
assignment by date makes this a direct query, and it is always correct — the
assignment shown is the one that covers today.

---

### UC-1.3 — View an employee's detail

**Preconditions**
- The employee exists

**Flow**
1. HR selects an employee from the list.
2. The detail page opens.
3. It shows identity and the full list of assignments, ordered by start date.
4. The assignment covering today is visually marked as current.

**Rules**
- The detail page is read-only. Editing is done from the edit form (UC-1.4).

**Outcome**
- The HR sees who the employee is and where they have worked.

**Rationale — why the detail page is read-only.**
Editing is a deliberate act that belongs in a form with validation, not in a
view meant for inspection. Separating the two makes both clearer.

---

### UC-1.4 — Edit an employee

**Preconditions**
- The employee exists

**Flow**
1. HR opens the employee detail page and selects "Edit".
2. The edit form opens with:
    - Identity fields (editable)
    - An assignments section showing the current assignment as an editable row
    - A "Create new assignment" button on that row
3. HR edits identity fields and, optionally, the current assignment row.
4. If the HR clicks "Create new assignment", a new empty row appears below the
   current one, with the same columns and a Save button.
5. HR fills in both rows. The current row's end date must be set — it is what
   ends the current assignment.
6. HR clicks Save.
7. The system validates. If valid, it performs the atomic operation. If not, it
   marks the offending fields and explains.

**Rules**
- Identity fields are editable, including matricule — subject to the same
  uniqueness constraint.
- The current assignment row is editable subject to UC-2.3 rules.
- Creating a new assignment is done by adding a row, not by opening a modal.
- All fields in both rows must be filled before Save succeeds.
- The current assignment's end date must be before the new assignment's start
  date.
- Neither range may overlap with another assignment for the same employee.
- The new assignment's start date must be before its end date.
- The new assignment's start date must be today or earlier. Future-dated
  assignments are not created here.
- If a new assignment row was added, the system verifies that all payroll for
  the current assignment has been recorded (see UC-2.1).

**Outcome**
- The employee's identity is updated, and — if a new assignment row was filled —
  the assignment change is applied atomically.

**Rationale — why the new assignment is a row, not a modal.**
The current assignment's end date and the new assignment's start date are
constrained relative to each other. Showing them in the same view makes the
relationship visible, and makes the validation feel natural rather than
arbitrary.

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
- Deleting an employee deletes their assignments.

**Outcome**
- The employee no longer exists.

**Rationale — why deletion is restricted.**
An employee with payroll is part of the historical record. Deleting them would
orphan payroll rows or force a cascade that destroys history. Restricting
deletion keeps the record intact.

---

## 2. Assignment Management

Assignments are managed from the employee edit form. There is no separate
assignment area.

### UC-2.1 — Create a new assignment

**Preconditions**
- The employee exists and has a current assignment
- All payroll for the current assignment has been recorded

**Flow**
1. HR opens the employee edit form.
2. The current assignment row is shown.
3. HR clicks "Create new assignment".
4. A new empty row appears below, with the same columns and a Save button.
5. HR fills in the new assignment's country, department, role, employment type,
   start date, and end date.
6. HR fills in the current assignment's end date.
7. HR clicks Save.
8. The system validates:
    - All fields present in both rows
    - Current end date before new start date
    - No overlap with other assignments
    - New start date before new end date
    - New start date is today or earlier
    - All payroll for the current assignment is recorded
9. If valid, the system applies the change atomically: sets the current
   assignment's end date and creates the new assignment.
10. The new assignment replaces the current one in the form.

**Rules**
- The operation is atomic.
- Every field in both rows is required.
- All validation rules apply; any failure blocks the save.
- "All payroll for the current assignment has been recorded" means: for every
  payroll period between the current assignment's start date and its new end
  date, this employee has a payroll summary.

**Outcome**
- The employee has a new assignment. The previous one is ended and remains in
  the detail page's history.

**Rationale — why all payroll must be recorded first.**
A new assignment supersedes the current one. If the HR could create it before
finishing payroll for the current assignment, payroll for earlier periods would
have no assignment to reference — or would reference an assignment whose
placement no longer applies. Requiring completion first keeps the model
consistent.

**Rationale — why future-dated assignments are not created here.**
If a new assignment could start in the future, the system would have two
assignments that "should" apply at different times, and the notion of "current"
would be ambiguous. Requiring the new assignment to start today or earlier
keeps exactly one assignment covering today.

---

### UC-2.2 — Create a new assignment from the payroll form

**Preconditions**
- HR is recording a payroll summary
- The assignment rendered by the system is not the one that should apply

**Flow**
1. During payroll recording, the system renders the assignment covering the
   payroll period.
2. If this assignment is not correct, HR selects "Correct the assignment".
3. The same two-row editing view opens as in UC-2.1, adapted to the payroll
   context.
4. HR fills in both rows and clicks Save.
5. The system validates and applies the atomic operation.
6. Control returns to the payroll form, with the corrected assignment rendered.

**Rules**
- Same rules as UC-2.1.
- The new assignment's end date must cover the payroll period being recorded.

**Outcome**
- The assignment is corrected, and payroll recording continues.

**Rationale — why this exists.**
The assignment rendered should already be correct. This path exists for the case
where it is not, and the HR Manager discovers the problem while recording
payroll. Sending them to the employee edit form and back would interrupt the
flow for a correction that can be made in place.

---

### UC-2.3 — Edit an assignment

**Preconditions**
- The assignment exists and covers today

**Flow**
1. HR opens the employee edit form.
2. The current assignment row is shown, with all columns editable.
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

**Outcome**
- The assignment is updated within the allowed constraints.

**Rationale — why placement is frozen once payroll exists.**
Payroll references the assignment. Changing the placement would silently change
what the payroll reports. Freezing placement after payroll exists prevents this.
The end date remains editable because extending or shortening an assignment
does not alter what was paid.

---

### UC-2.4 — View assignments

**Preconditions**
- The employee exists

**Flow**
1. HR opens the employee detail page.
2. The assignments section lists all assignments, ordered by start date.
3. The assignment covering today is visually marked as current.

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
- The employee has an assignment covering the period

**Flow**
1. HR opens the payroll recording screen.
2. HR selects a payroll period. Only open periods are offered.
3. HR selects an employee, or creates a new one in a modal (UC-1.1).
4. The system resolves the assignment whose date range covers the payroll
   period and renders it.
5. If the assignment is not correct, HR selects "Correct the assignment"
   (UC-2.2).
6. The system checks whether a payroll summary already exists for
   `(employee, period)`.
    - If a `DRAFT` exists, it is loaded for editing.
    - If a `VALIDATED` exists, the HR cannot continue.
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
- The period must be open. The period picker offers only open periods; the API
  rejects closed periods as defense in depth.

**Outcome**
- A payroll summary exists in `DRAFT` or `VALIDATED` status.

**Rationale — why only open periods are offered.**
A closed period is a finalized historical record. Recording payroll into it
would contradict its snapshot. Restricting the picker to open periods makes the
rule visible rather than an error discovered after filling out a form.

**Rationale — why the assignment is resolved, not chosen.**
For any payroll period, exactly one assignment can cover it — assignments do not
overlap. There is nothing to choose. Rendering the resolved assignment and
offering a correction path is simpler and less error-prone than presenting a
list.

---

### UC-3.2 — Edit a draft payroll summary

**Preconditions**
- The summary exists and is in `DRAFT`

**Flow**
1. HR loads the payroll list, filters to the employee or period, and opens the
   draft.
2. HR edits the amounts.
3. HR saves.

**Rules**
- Only `DRAFT` summaries can be edited. Attempting to edit a `VALIDATED` summary
  is rejected.

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
- Validation is one-way. There is no unvalidate.
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
3. Drafts that pass validation become `VALIDATED`.
4. Drafts that fail are reported, with reasons.
5. HR is shown the result: how many succeeded, how many failed, and why.

**Rules**
- Bulk validation does not fail atomically. Drafts that pass are validated;
  drafts that fail are left as `DRAFT`.
- The system reports each failure.

**Outcome**
- Some or all drafts are validated. Failures are visible.

**Rationale — why bulk validation is not atomic.**
The HR Manager wants to validate what they can and see what remains. Failing the
whole batch because one draft is incomplete would force them to find and fix
each one before any validation happens.

---

### UC-3.6 — Browse the payroll list

**Preconditions**
- None

**Flow**
1. HR opens the payroll list.
2. The list is initially empty until filters are applied. A date range is
   required to load results.
3. HR sets a date range and optionally additional filters.
4. The list updates.

**Filters**
- Date range (required) — start and end date, not a payroll period
- Employee
- Role
- Department
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
- The date range resolves the payroll periods that fall within it.

**Outcome**
- A filtered, paginated list of payroll summaries.

**Rationale — why the date range is required.**
Without a range, the list would return every payroll summary in the system —
tens of thousands of rows with no useful context. Requiring a range makes the
query meaningful and keeps the result bounded.

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
7. If the previous period is not closed, the system offers to close it first.
8. If the HR declines, the process stops.
9. If the HR accepts, the system opens the previous period's close flow
   (UC-4.4). The HR completes it, then returns to create the new period.
10. The period is created with `snapshotStatus = NOT_COMPUTED`.

**Rules**
- Frequency is required and is `MONTHLY` or `BI_WEEKLY`.
- Start date must be before end date.
- Monthly periods span 28–31 days; bi-weekly periods span exactly 14 days.
- Name is generated, not entered.
- Periods of the same frequency must not overlap.
- The previous period of the same frequency must be closed.

**Outcome**
- A new payroll period exists, ready for payroll recording.

**Rationale — why the previous period must be closed.**
The snapshot is cumulative. A report over a range that includes a closed period
starts from that period's snapshot, which assumes every earlier period is also
closed. Requiring the previous period to be closed preserves this. It also
ensures the HR Manager confirms the previous period is complete before starting
the next one.

---

### UC-4.2 — List payroll periods

**Preconditions**
- None

**Flow**
1. HR opens period management.
2. The list shows every period, ordered by start date descending, with its
   status.

**Columns**
- Name
- Frequency
- Start date
- End date
- Snapshot status

**Outcome**
- The HR sees every period and its current state.

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
- Editing changes name, dates, or frequency, subject to the same overlap and
  duration rules as creation.

**Outcome**
- The period is edited or deleted, or the action is rejected.

---

### UC-4.4 — Close a payroll period

**Preconditions**
- Every payroll summary in the period is `VALIDATED`
- The period's `snapshotStatus` is `NOT_COMPUTED`

**Flow**
1. HR opens the period and selects "Close".
2. The system verifies all summaries in the period are `VALIDATED`. If any are
   still drafts, the close is rejected and the drafts are listed.
3. The system computes the snapshot: aggregates and rankings.
4. The system stores the snapshot and sets the period's `snapshotStatus` to
   `DRAFT`.
5. The system presents the snapshot to the HR: totals by country, department,
   and role, plus any data-quality warnings.
6. HR reviews the snapshot.
7. If there are critical warnings, HR must resolve them before validating.
8. HR validates the snapshot.
9. The system sets `snapshotStatus` to `VALIDATED`. The period is now closed.

**Rules**
- Close requires all summaries to be validated.
- The snapshot is generated once.
- Validation of the snapshot is the act of closing the period.
- A closed period accepts no further payroll summaries, and no edits.

**Outcome**
- The period is closed. Its snapshot is stored and immutable.

**Rationale — why close is a two-step review.**
The snapshot is the historical record. Computing it and immediately closing
would make the HR Manager commit to a result they have not seen. Presenting the
snapshot — with warnings — turns closing into a deliberate acceptance.

---

## 5. Reporting

### UC-5.1 — Generate an aggregate report

**Preconditions**
- None

**Flow**
1. HR opens reporting and selects the aggregate report.
2. HR sets a date range.
3. HR selects one or more group keys, or none.
4. HR optionally enables component breakdown and statistics.
5. HR submits.
6. The system checks for blockers.
    - If a blocker exists, the report is not produced and the blocker is shown.
7. Otherwise, the system produces the report and shows any warnings.
8. The HR sees the results.

**Rules**
- Group keys are drawn from a fixed set: country, department, role, employment
  type, currency, origin country.
- Statistics require country, department, and role as group keys.
- The report is returned in the reference currency.
- Warnings do not block the report.

**Outcome**
- A table of totals, with optional breakdown and statistics, plus any warnings.

---

### UC-5.2 — Generate a ranking report

**Preconditions**
- The payroll period exists
- The period has a validated snapshot, or live validated summaries

**Flow**
1. HR opens reporting and selects the ranking report.
2. HR selects a payroll period.
3. HR selects a country, department, and role.
4. HR selects TOP or BOTTOM and a limit (up to 5).
5. HR submits.
6. The system returns the ranked employees.

**Rules**
- The report is period-scoped, not range-scoped.
- The limit is at most 5.
- The country, department, and role must be complete.

**Outcome**
- A list of up to 5 employees with their names, ranks, and amounts.

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

**Rationale — why blockers and warnings are distinguished.**
Some conditions make the report impossible to produce correctly — a missing FX
rate means conversion cannot happen. Others make it incomplete but still
meaningful. Blocking in the first case prevents wrong numbers; warning in the
second prevents dead ends.

---

## 6. Cross-Cutting Behaviors

### Pagination

Every list — employees, payroll, periods, report results — is paginated
server-side. The default page size is 20.

### Data entry is strict

Payroll summaries are validated at save time. A summary that contradicts its
assignment, or that is saved against a closed period, is rejected. The HR
Manager is told what to fix.

### Reporting is lenient

Reports are produced even when the data is incomplete, with warnings. Only
conditions that make the result incorrect block the report.

### No corrections after validation

A validated payroll summary is immutable. A closed period accepts no changes.
Corrections are out of scope.