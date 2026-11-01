# Use Cases

> How the HR Manager uses the system. Each use case states the actor, the
> preconditions, the flow, the rules that apply, and the outcome.

The system has one actor: the **HR Manager**. All use cases are initiated by them.

A principle that runs through the whole system: when an action cannot proceed,
the system explains why and offers a way to resolve it. The HR Manager is never
left with a silent failure.

**Note on what is built.** Section 4 (Payroll Period Management) describes a
flow that is designed but not built. The milestones list records `M11` as
0/4 — periods are seeded, not managed. The section is kept here as design
documentation for the intended behavior.

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
   department, role, employment type, payroll frequency.
5. HR submits.
6. The system creates the employee and the first assignment together, with
   `inEffect = true`.
7. The HR is returned to the employee list.

**Rules**
- Matricule is required and unique across the organization.
- All identity fields are required except `originCountry` and `birthDate`,
  which are nullable.
- The first assignment is required.
- The assignment's start date and payroll frequency are required.

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
- In-effect country of employment (`anyAssignmentCountryIds`)
- In-effect department (`anyDepartmentIds`)
- In-effect role (`anyRoleIds`)
- In-effect employment type (`employmentTypes`)
- Origin country (`anyOriginCountryIds`)

**Rules**
- Search and filters combine. All are optional.
- Results are paginated (default size 20).
- All filters accept a set of IDs or enum values (multi-select).

**Outcome**
- A filtered, paginated list of employees.

---

### UC-1.3 — View and edit an employee

> **Note:** the system does not have a separate read-only employee detail page.
> The employee list row opens the edit form directly. The form is both the
> view and the edit surface. See M9 in the milestone table.

**Preconditions**
- The employee exists

**Flow**
1. HR selects an employee from the list.
2. The edit form opens.
3. It shows identity fields, the in-effect assignment as an editable row, and
   a "Create new assignment" button.
4. HR edits identity fields and, optionally, the in-effect assignment row.
5. If the HR clicks "Create new assignment", a new empty row appears below.
6. HR fills in both rows.
7. HR clicks Save.
8. The system validates. If valid, it performs the atomic assignment change.
   If not, it marks the offending fields and explains.

**Rules**
- Identity fields are editable.
- The in-effect assignment row is editable subject to UC-2.3 rules.
- All fields in both rows must be filled before Save succeeds.
- The in-effect assignment's end date must be before the new assignment's
  start date (when a new assignment is created).
- Neither range may overlap with another assignment for the same employee.
- If the employee already has a payroll summary linked to the in-effect
  assignment, only the assignment's `endDate` and `inEffect` status remain
  editable.

**Outcome**
- The employee's identity is updated, and — if a new assignment row was
  filled — the assignment change is applied atomically. The new assignment
  becomes in-effect.

---

### UC-1.5 — Delete an employee

**Preconditions**
- The employee exists and has no payroll, including drafts

**Flow**
1. HR opens the employee edit form and selects "Delete".
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
   payroll frequency, start date, and end date.
6. HR fills in the in-effect assignment's end date.
7. HR clicks Save.
8. The system validates:
    - All fields present in both rows
    - In-effect end date before new start date
    - No overlap with other assignments
    - New start date before new end date (when end date is provided)
9. If valid, the system applies the change atomically: sets the in-effect
   assignment's end date, marks it not in-effect, and creates the new assignment
   with `inEffect = true`.
10. The new assignment replaces the in-effect row in the form.

**Rules**
- The operation is atomic.
- Every field in both rows is required.
- Any validation failure blocks the save and marks the offending field.
- The client must send `toClose` for the previous in-effect assignment in the
  same request; otherwise the system rejects the creation with
  `InEffectNotClosed`.

**Outcome**
- The employee has a new in-effect assignment. The previous one is ended and
  remains in the edit form's history.

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
  overlap rule and to the requirement that `endDate > startDate` when endDate
  is provided.
- If the assignment has **payroll** linked: only the `endDate` is editable,
  and the new end date must still cover every payroll period that references
  the assignment. Any attempt to change a structural field (country,
  department, role, employmentType, payrollFrequency, startDate) is rejected
  with `ImmutableAfterPayroll`.
- The end date must not fall before the latest linked payroll period's start
  date.
- Editing the end date — including extending it — does not change the
  `inEffect` flag.

**Outcome**
- The assignment is updated within the allowed constraints.

---

## 3. Payroll Recording

### UC-3.1 — Record a payroll summary

**Preconditions**
- The payroll period exists and is open (`closed = false`)

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
- Total must equal the sum of components (enforced with scale-2 rounding to
  survive SQLite's REAL storage).
- `(employee, period)` must be unique.
- The period must be open (`closed = false`).

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
2. HR edits the amounts, or changes the linked assignment.
3. HR saves.

**Rules**
- Only `DRAFT` summaries can be edited.
- If the linked assignment changes, the new assignment must belong to the same
  employee, be in-effect, and cover the period.

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
2. The system checks: the period is open, the assignment is in-effect, and the
   assignment covers the period.
3. If all pass, the summary transitions to `VALIDATED`.
4. The summary becomes immutable.

**Rules**
- Validation is one-way.
- A validated summary can no longer be edited or deleted.
- The single-validation call runs in its own transaction (`REQUIRES_NEW`), so
  a failure on one summary does not roll back others when called in a loop.

**Outcome**
- The summary is `VALIDATED` and immutable.

---

### UC-3.5 — Validate all drafts in a period

**Preconditions**
- The payroll period exists
- At least one draft exists in the period

**Flow**
1. HR opens the period and selects "Validate all drafts".
2. The system reads matching drafts in keyset-paginated batches.
3. For each draft, it applies the same rules as UC-3.4.
4. Drafts that pass become `VALIDATED`.
5. Drafts that fail are reported, with reasons.
6. HR sees the result: total count, validated count, and per-row failures.

**Rules**
- Bulk validation does not fail atomically. Successful rows commit; failures
  are reported per-row.
- Uses keyset pagination (a cursor) so the working set shrinking does not skip
  rows.
- If a row lost a race between the read and the update (someone else validated
  or deleted it), it is reported as `SUMMARY_NOT_DRAFT`. The row is no longer
  in `DRAFT` — the goal is achieved, just not by this call.

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
- Role
- Department
- Country
- Employment type
- Status (`DRAFT` / `VALIDATED`)
- Payroll frequency (`MONTHLY` / `BI_WEEKLY`)
- `nonExistent=true` — inverted query; returns employees with an in-effect
  assignment who have **no** payroll summary for the period

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
- The `nonExistent` mode is the query behind the "missing payroll summary"
  warning on the aggregate report.

**Outcome**
- A filtered, paginated list of payroll summaries.

---

## 4. Payroll Period Management

> **Not built.** Milestone `M11` is 0/4. Periods are seeded. The use cases
> below describe the intended design, kept for reference.

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
10. The period is created with `closed = false`.

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
- Closed (boolean)

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

**Flow**
1. HR opens the period and selects "Close".
2. The system verifies all summaries are `VALIDATED`. If any are drafts, the
   close is rejected, the drafts are listed, and the reason is explained.
3. The system builds the snapshot: aggregates, rankings, percentiles.
4. The system stores the snapshot with `status = VALIDATED` (replacing any
   existing DRAFT rows for the period).
5. The period is marked `closed = true`.

**Rules**
- Close requires all summaries to be validated.
- Validating the snapshot is the act of closing the period.
- A closed period accepts no further payroll summaries and no edits.

**Outcome**
- The period is closed. Its snapshot is stored with `status = VALIDATED` and
  is immutable.

---

## 5. Reporting

### UC-5.1 — Generate an aggregate report

**Preconditions**
- None

**Flow**
1. HR opens reporting and selects the aggregate report.
2. HR sets a date range.
3. HR selects zero or more group keys from the supported set.
4. HR optionally enables component breakdown and statistics.
5. HR submits.
6. The system resolves the MONTHLY periods in the range and rebuilds the
   aggregate snapshot for each open period.
7. The system checks for blockers.
    - If a blocker exists, the report is not produced, and the blocker is
      explained.
8. Otherwise, the system reads the snapshots for the range and produces the
   report, with any warnings.
9. HR sees the results.

**Group keys (supported)**
- `COUNTRY`
- `DEPARTMENT`
- `ROLE`
- `EMPLOYMENT_TYPE`
- `CURRENCY` — accepted by the request, but statistics cannot be computed per
  currency; requesting statistics with `CURRENCY` returns `VALIDATION_FAILED`.
- `ORIGIN_COUNTRY` — rejected at validation with `UNSUPPORTED_GROUP_KEY`.

**Rules**
- **Statistics require at most one group key.** Requesting statistics with more
  than one dimension returns `VALIDATION_FAILED`.
- Statistics are computed **per frequency** — the response carries separate
  monthly and bi-weekly distributions. When statistics are requested, the
  line's `amount` becomes the sum of the two frequency totals.
- The report is returned in the reference currency (USD today).
- Warnings do not block the report. Blockers do.

**Outcome**
- A table of totals, with optional breakdown and statistics, plus any warnings.

---

### UC-5.2 — Generate a ranking report

**Preconditions**
- The payroll period exists

**Flow**
1. HR opens reporting and selects the ranking report.
2. HR selects a payroll period and one group key (or "global" for no key).
3. HR optionally selects the dimension value (country, department, role, or
   employment type — matching the chosen group key).
4. HR selects TOP or BOTTOM.
5. HR submits.
6. The system rebuilds the ranking and percentile snapshots for the period
   (if the period is open).
7. The system returns the top 5 and bottom 5 earners, plus the 7-point
   percentile distribution for the selected group.

**Rules**
- The report is period-scoped, not range-scoped.
- The group key and dimension fields must agree: `groupKey = COUNTRY` requires
  `countryId` and forbids `departmentId`, `roleId`, `employmentType`. Any
  mismatch returns `VALIDATION_FAILED`.
- `groupKey = null` requires all dimension fields to be null (the global
  partition).
- The limit is capped at 5.
- Rankings are by **monthly rate**, not raw amount, so monthly and bi-weekly
  earners are comparable.
- The response includes a `positionContext` (min, p10, p25, p50, p75, p90,
  max) when the group has at least 10 employees. Groups below 10 have a null
  `positionContext`.
- Each earner's `percentile` is interpolated from the distribution and
  expressed in basis points (0–10000).

**Outcome**
- Top 5 and bottom 5 employees with names, ranks, monthly rates, and
  interpolated percentiles, plus the position context for the group.

---

### UC-5.3 — Generate a comparison report

> **Not built.** The `ReportKind.COMPARISON` enum value exists as a
> placeholder, but no code produces it. `docs/planning.md` records the reason.

---

### UC-5.4 — Handle blockers and warnings

**Preconditions**
- HR has attempted to generate a report

**Flow**
1. If the report is blocked, the system returns a `REPORT_BLOCKED` response
   with the blocker(s) and any warnings collected before the abort. The report
   is not produced.
2. If the report is produced with warnings, the HR sees the report and the
   warnings alongside it.

**Rules**
- Blockers prevent the report. Warnings do not.
- Each blocker or warning states the issue and how the system handled it.
- The conditions currently classified as blocker vs warning are listed in
  `docs/adr/004-enforce-entry-warn-reporting.md`.

**Outcome**
- The HR knows whether the report is reliable, and what to fix if it is not.

---

## 6. Cross-Cutting Behaviors

### Pagination

Every list — employees, payroll, periods, report results — is paginated
server-side. The default page size is 20, max 100.

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

### Snapshot lifecycle

Reports over open periods rebuild the snapshot before reading; reports over
closed periods read the frozen snapshot. See `docs/architecture.md` for the
full design.