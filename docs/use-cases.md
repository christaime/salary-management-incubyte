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
> view and the edit surface.

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

### UC-4.1 — List payroll periods

**Preconditions**
- None

**Flow**
1. HR opens the pay-schedule list at `/payroll-periods`.
2. The list loads automatically (no initial filter required).
3. HR optionally sets filters and clicks Search.
4. The list updates.

**Filters**
- Keyword (name, contains, case-insensitive)
- Frequency (`All` / `Monthly` / `Bi-Weekly`)
- Status (`All` / `Open` / `Closed`)
- Date range (`From` / `To`)

**Columns**
- Name
- Frequency
- Start date
- End date
- Settled (Open / Settled)
- Actions (Edit, Delete, Settle — Settle is only shown for MONTHLY and not-settled rows)

**Rules**
- The list is server-paginated (default size 20).
- Sort is fixed on `startDate DESC` — no sorting controls on the columns.
- Filters and Search are manual: filters are applied only when Search is
  clicked, or cleared with Reset.

**Outcome**
- A filtered, paginated list of every payroll period.

---

### UC-4.2 — Create a payroll period

**Preconditions**
- None

**Flow**
1. HR opens the pay-schedule list and selects "New pay schedule".
2. A form opens with: name, frequency, start date, end date, and (for MONTHLY
   only) an "Also create bi-weekly schedules" checkbox.
3. HR fills in the name, frequency, and start date. The end date is
   auto-suggested by the form based on the frequency and start date, and can
   be adjusted by the HR Manager.
4. If the frequency is MONTHLY and the HR Manager opts in to the cascade:
    - The system asks the server for the bi-weekly periods that would fall
      inside the monthly range (a read-only preview, no rows created).
    - The preview is split into two buckets: sub-periods that would be
      created, and sub-periods that already exist on the chain.
    - The HR Manager can rename any sub-period in the "would create" bucket
      before submitting.
5. HR submits.
6. The system validates:
    - Name is required, 2–100 characters.
    - Start date is before end date.
    - MONTHLY spans 28–31 days inclusive; BI_WEEKLY spans exactly 14 days.
    - Name is unique per frequency.
    - Date range does not overlap another period of the same frequency.
7. If valid:
    - Without cascade: a single `POST /payroll-periods` creates the period.
    - With cascade: a single `POST /payroll-periods/batch` creates the monthly
      period and every bi-weekly sub-period the HR Manager accepted, in one
      call. The batch is **not atomic** — each item commits independently. If
      some items fail (typically a race against a concurrent create), the
      editor stays open and shows the failed items, so the HR Manager can
      retry them.
8. On full success, the HR Manager is returned to the list.

**Rules**
- Name is entered by the HR Manager (not generated from the dates).
- MONTHLY must span 28–31 days inclusive; it is not required to align with a
  calendar month. (A monthly cycle can close on the 25th, be a 4-4-5 cycle,
  etc.)
- BI_WEEKLY must span exactly 14 days.
- Frequency is `MONTHLY` or `BI_WEEKLY`.
- Name and date range are unique per frequency; monthly and bi-weekly may
  overlap each other.
- The cascade is optional and explicit: the HR Manager opts in, sees the
  preview, renames sub-periods if they want, and confirms.
- The cascade's sub-periods continue the existing bi-weekly chain — they do
  not restart at the monthly's start date. If no bi-weekly precedes the
  monthly, the chain starts at the monthly's start. Each sub-period spans
  exactly 14 days; the last one may extend past the monthly's end date.
- Sub-period names restart at `BW1` per monthly period and use the
  **monthly's** month and year, not the sub-period's own.

**Outcome**
- A new payroll period exists. If the cascade was requested, the corresponding
  bi-weekly periods exist as well. All are open.

---

### UC-4.3 — Edit a payroll period

**Preconditions**
- The period exists

**Flow**
1. HR selects "Edit" on a row from the list, or navigates to
   `/payroll-periods/:id/edit`.
2. The system loads the period.
3. The form preloads with the period's current name, frequency, start date,
   end date, and a `referenced` flag indicating whether payroll summaries
   exist for it.
4. If the period is **referenced** (has payroll summaries), the form:
    - shows an amber notice explaining that only the name can be changed,
    - locks the frequency, start date, and end date fields,
    - leaves the name field editable.
5. HR edits the allowed fields.
6. HR submits.
7. The system validates and saves. If the period is settled, or the request
   touches a field it may not, the save is rejected.

**Rules**
- A **settled** period is immutable: name, dates, frequency. No save is
  allowed.
- A **referenced** period (payroll summaries exist) may only have its
  **name** changed. Dates and frequency are frozen. The rule exists because
  payroll summaries are keyed on the period, and reports assume the period's
  date range matches the summaries' range — a reschedule would silently move
  recorded payroll to a different reporting window.
- An **unreferenced** open period can be fully edited: name, dates,
  frequency. The same calendar validity and uniqueness rules as UC-4.2
  apply.
- A race (someone references the period between the load and the save) is
  handled by the same rule the frontend uses to pre-lock: the backend
  re-checks on save and returns the name-only constraint, which the editor
  surfaces.

**Outcome**
- The period is updated within the allowed constraints.

---

### UC-4.4 — Delete a payroll period

**Preconditions**
- The period exists

**Flow**
1. HR selects "Delete" on a row from the list.
2. A confirmation dialog opens.
3. HR confirms.
4. The system validates: the period must be neither settled nor referenced.
5. If valid, the period is deleted.
6. If not, the deletion is rejected with an explanation:
    - `PERIOD_CLOSED` → settled periods cannot be deleted.
    - `PERIOD_REFERENCED` → periods with payroll summaries cannot be deleted.

**Rules**
- Deletion is rejected if the period is settled.
- Deletion is rejected if the period has any payroll summary, in any status.
- The batch endpoint (`DELETE /payroll-periods/batch`) exists but is not
  exposed from the list UI — the HR Manager deletes periods one by one. A
  "delete a monthly together with its sub-periods" action is a candidate
  follow-up, not shipped.

**Outcome**
- The period no longer exists. Its row and any state attached to it are gone.

---

### UC-4.5 — Settle a payroll period

Settling is a two-step flow: a read-only pre-flight, then the settle call.

**Preconditions**
- The period exists, is MONTHLY, and is not already settled

**Flow**
1. HR selects "Settle" on a monthly row from the list (or a settle action
   from the editor).
2. A dialog opens and immediately runs the **pre-flight** (`check-close`).
3. If the pre-flight returns **blockers**, the dialog switches to a
   "Settlement blocked" state:
    - blockers are listed (typically un-validated payroll summaries),
    - the primary action becomes a plain **Close** button (no retry inside the
      dialog),
    - HR must resolve the blockers (via the payroll list) and re-run the flow.
4. If the pre-flight succeeds (no blockers), the dialog shows:
    - the **schedules that would be settled** — the monthly anchor first,
      visually emphasised, followed by its nested bi-weekly sub-periods,
    - any **warnings** the HR Manager would be accepting (typically missing
      payroll summaries for employees who should have had one), in a scrollable
      panel,
    - an irreversibility checkbox the HR Manager must tick before the primary
      action is enabled.
5. If there are no warnings, the primary action is labelled **"Settle
   payroll"**. If there are warnings, it is labelled **"Accept and settle"**
   — the label change is the HR Manager's consent to the warnings.
6. HR clicks the primary action.
7. The system runs the **settle** (`close`) call:
    - re-runs the coverage checks,
    - builds the aggregate, ranking, and percentile snapshots for the monthly
      period,
    - promotes them from `DRAFT` to `VALIDATED`,
    - flips the `closed` flag on the monthly period and on every bi-weekly
      whose start date falls inside the monthly range.
8. If the settle succeeds, the dialog switches to a "Done" state showing the
   settled schedules, and the primary action becomes a plain **Close** button
   (the confirm is permanently disabled).
9. If the settle fails with a race condition (a blocker appeared between
   pre-flight and settle), the dialog returns to the **blocked** state with
   the new blockers. The HR Manager dismisses and re-runs the flow from step 1.
10. If the settle fails for any other reason (network, server), the dialog
    shows an error and offers a Retry that re-runs the pre-flight.

**Rules**
- Settling is atomic. Nothing is written unless every check passes and the
  transaction commits.
- Settling closes the monthly period **and** every bi-weekly period whose
  start date falls inside the monthly range, in one transaction.
- The irreversibility checkbox gates the primary action. Without it, the
  primary action is disabled.
- Warnings do not block the close — the HR Manager accepts them by proceeding
  through the "Accept and settle" label.
- Blockers block the close — the HR Manager cannot proceed until they are
  resolved.
- The settle call is the **only** writer of `VALIDATED` snapshot rows. No
  other code path promotes a snapshot.
- A settled period cannot be settled again.

**Outcome**
- The period and its nested bi-weeklies are settled. Their snapshots are
  `VALIDATED` and immutable. The period appears as "Settled" in the list.

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
> placeholder, but no code produces it.

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
  `docs/adr/004-enforce-entry-warn-reporting.md`. The settle flow reuses the
  same vocabulary — what blocks a report blocks a close.

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
closed periods read the frozen snapshot. The settle flow is the only writer
of `VALIDATED` snapshot rows. See `docs/architecture.md` for the full design.