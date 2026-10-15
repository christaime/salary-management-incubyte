# Requirements — Salary Management

## 1. Goal

Build a web application that lets ACME's HR Manager manage salary data for
10,000 employees across multiple countries and answer questions about how the
organization pays people.

The system **records** payroll amounts — it does not **compute** them. Tax rules,
gross-to-net calculations, social contributions, and payslip generation are
explicitly out of scope.

The system answers questions about how the organization **pays** people. It is
not a headcount or directory tool — it reports payroll costs and compensation
patterns, not who works where.

## 2. Scope & Features

### Employee management

- Create, read, update, and delete employee records
- Fields: matricule, name, email, birth date, origin country
- **The matricule is required, unique across the organization, and entered by
  the HR Manager.** The system does not generate it — it has no knowledge of
  recruitment or the organization's numbering scheme.
- **Creating an employee requires defining the employee's first assignment.**
  An employee without an assignment has no placement in the organization and
  cannot be used for payroll or reporting.
- Search by matricule, name, email, or current placement (country, department,
  role, employment type)
- The employee list shows identity plus current placement
- Server-side pagination
- **Deletion is allowed only if the employee has no payroll, including drafts.**
  Deleting an employee also deletes their assignments.

**Rationale — why Employee holds no placement.**
An employee's country, department, and role are historical facts, not properties
of the person. They change over time. Modeling them on Employee would either
force a single "current" value (losing history) or require per-employee
versioning (complex). Instead, placement is recorded on assignments, which are
naturally dated.

**Rationale — why the employee list shows current placement.**
The HR Manager uses the list to find people and to see where they work. Showing
the current assignment makes the list useful at a glance, and makes filtering by
placement a direct query rather than a date-range subquery.

### Employee assignments

- An **EmployeePeriodicAssignment** records where an employee worked and in what
  role, over a span of time
- Fields: employee, start date, end date (nullable), country, department, role,
  employment type (enum), and a `current` flag
- Assignments for the same employee must not overlap
- **Exactly one assignment per employee is marked as current.** Normally this is
  the assignment whose date range contains today. When the most recent
  assignment has ended and no new one has begun, the most recent assignment
  remains current.
- **Creating a new assignment ends the current one.** The system sets the current
  assignment's end date, marks it as non-current, and creates the new assignment
  as current. This is a single atomic operation.
- **Editing rules:**
    - If the assignment has no payroll: all fields are editable
    - If the assignment has payroll: only the end date is editable, and the new
      end date must still cover every payroll period that references the assignment

**Rationale — why assignments are a distinct concept.**
Payroll depends on where and how an employee was placed. Tracking placement over
time allows payroll records to reference what was actually true during the
period, rather than trusting free-form data entry.

**Rationale — why the current flag exists.**
Without it, "the employee's current placement" would require a date-range query
on every read. The flag makes it a stored fact: at most one assignment per
employee is current, and searching or displaying current placement is a direct
join.

**Rationale — why placement is frozen once payroll exists.**
Because payroll references the assignment, changing the assignment's placement
would silently change the placement recorded on the payroll. Freezing placement
once any payroll references the assignment prevents this. Only the end date
remains editable, and only within the constraint that all referenced payroll
periods remain covered.

**Employment type values.**
Employment type is an enum with three values: `FULL_TIME`, `PART_TIME`, and
`CONTRACTOR`. The set is small, fixed, and known at design time, and no metadata
beyond the name is needed. Modeling it as an enum avoids a reference table and a
join that would add no benefit at this scale.

### Payroll summaries

- A payroll summary links an employee to a payroll period and to the employee's
  assignment for that period
- Country, department, and role are taken from the assignment
- The currency and amounts are recorded on the summary; the currency is the one
  in effect for the assignment's country at the time of recording
- Amounts: base, variable, allowances, employer taxes, total, currency
- Lifecycle: `DRAFT` → `VALIDATED`. Validated summaries are immutable.
- Unique per `(employee, payroll period)`
- Validation on save:
    - Components non-negative
    - `total = base + variable + allowances + employerTaxes`
    - The assignment must belong to the same employee
    - The payroll period must fall entirely within the assignment's date range
    - The currency must match the assignment's country currency
    - The period must not be closed
- **Validation of a draft may be performed one at a time or in bulk.** Bulk
  validation validates every draft in a period that passes validation and
  reports failures; it does not fail atomically.

**Rationale — why payroll references an assignment instead of duplicating
placement.**
If payroll carried its own country, department, and role, the system could
record a payroll that contradicts the assignment. By referencing the assignment,
that contradiction becomes impossible — consistency is structural.

**Rationale — why currency is stored on the payroll summary.**
The currency is a fact about the payment, not a property of the placement. It
records what was actually paid, in the currency it was paid in. It is validated
against the assignment's country on save, but not re-derived on read.

**Rationale — why the draft/validated lifecycle exists.**
Payroll data is entered over time and may be incomplete while being assembled.
A draft state lets the HR Manager record and revise entries without exposing them
to reports. Validating a summary is the deliberate act that declares it final.
Corrections after validation are out of scope.

### Payroll list

- The payroll list is filtered by a **date range**, not by a specific payroll
  period. The system resolves the periods that fall within the range.
- Filters: employee, role, department, country, employment type, date range
- Columns: employee, payroll period, country, department, role, employment type,
  total and currency
- Server-side pagination

**Rationale — why the list filters by date range rather than by period.**
The HR Manager often wants to look across periods — "what did we pay in Q1?" —
not just at one period. Filtering by date range matches how the reports work and
removes a step from the common query.

### Reference data

- **Country** — ISO code, name, currency code
- **Department** — id, name (canonical list)
- **Role** — id, name (canonical list)
- **PayrollPeriod** — name, start date, end date, frequency, and a snapshot
  status (`NOT_COMPUTED`, `DRAFT`, `VALIDATED`)
- **CurrencyRate** — the current rate per currency pair against the reference
  currency

**Rationale — why departments and roles are canonical lists.**
In the spreadsheet, department and role names are free text, so grouping is
unreliable ("Eng." vs "Engineering"). Enforcing a fixed list ensures that every
payroll record uses a canonical value and aggregations are exact.

**Rationale — why payroll periods are organization-wide.**
A single HR Manager manages payroll across countries. Periods are shared, not
per-country. This keeps reporting simple.

**Rationale — why periods carry a frequency and may overlap across frequencies.**
Different groups of employees may be paid on different cycles. Periods of the
same frequency must not overlap; periods of different frequencies may.

**Rationale — why currency rates are keyed by currency, not country.**
An exchange rate describes a currency pair, not a country. Keying by currency
allows one rate to apply to every country that uses that currency.

**Rationale — why currency rates are not historical.**
Once a payroll period's snapshot is validated, its converted amounts are frozen.
Reports over closed periods never re-convert. Historical rate precision is
therefore unnecessary — the rate table holds current values only.

### Payroll period management

- Create a period with a frequency, start date, and end date. A name is
  generated from the dates.
- Overlap with a period of the same frequency is rejected.
- **Recording payroll for a new period requires that the previous period of the
  same frequency is closed.** If it is not, the system offers to close it first;
  declining stops the process.
- **A period can be edited or deleted only if it has no payroll.**
- **Closing a period is a two-step process.** The system computes the snapshot
  in `DRAFT`, presents it to the HR Manager along with data-quality warnings, and
  the HR Manager validates it. Validation closes the period.
- The period list shows each period with its status.

**Rationale — why the previous period must be closed.**
Two reasons. First, the snapshot is cumulative: a report starting from a
snapshot assumes every earlier period is closed. Requiring the previous period
to be closed preserves the checkpoint chain. Second, closing is the HR Manager's
declaration that the period's data is complete — requiring it before recording
the next period means the HR Manager confirms completeness before moving on.

**Rationale — why closing requires reviewing the snapshot.**
The snapshot is the historical record. Validating it is the moment the HR
Manager accepts that the period's data is final. Presenting the snapshot — with
warnings — makes that acceptance deliberate rather than a button press.

### Reporting

- Reports are defined by a date range and aggregate all payroll periods within it
- All figures are presented in a single reference currency (USD)
- Reports support a data-quality check:
    - **Blockers** prevent report generation (missing FX rate, invalid range,
      future period)
    - **Warnings** allow the report to be produced and state how the system handled
      the issue (employees with an active assignment but no validated summary,
      partial country coverage)
- Reports fall into three kinds:
    - **Aggregate** — totals grouped by zero or more dimensions
    - **Ranking** — the top or bottom earners within a specific period and group
    - **Comparison** — two aggregate reports side by side

**Rationale — why reports are not persisted.**
A report is a query, not a record. Persisting it would raise questions about
staleness and versioning. Generating on demand keeps the answer current.

**Rationale — why some conditions block and others warn.**
Some conditions make the report impossible to produce correctly. Others make it
incomplete but still meaningful. Blocking in the first case prevents wrong
numbers; warning in the second prevents dead ends.

### Data

- Deterministic seed script generating 10,000 employees across ~8 countries and
  ~10 departments, with assignments and several periods of payroll summaries
- Assignments are generated first; payroll summaries are derived from them, so
  consistency is structural

## 3. Deliberately Out of Scope

| Excluded | Reason |
| :--- | :--- |
| Payroll execution (tax computation, net pay, payslips) | Legally jurisdiction-specific; the brief asks for salary data and reporting, not payroll processing |
| Payroll corrections after validation | Validated summaries are immutable; correcting them would require an audit trail |
| Itemized salary components | A single amount per component is sufficient for reporting |
| Multiple concurrent assignments per employee | Would require a join table between employee, period, and assignment |
| Mid-period assignment changes | Payroll is recorded per period; assignment changes take effect at period boundaries |
| Assignment exceptions | Payroll and assignment must be consistent |
| Historical exchange rates | Closed periods freeze their conversions; open periods use current rates |
| Excel import | A deterministic seed script replaces data onboarding |
| PDF / Excel export | The UI is the presentation layer |
| Salary distribution buckets | Requires individual salary values; the snapshot stores totals and rankings |
| Top N beyond 5 | The snapshot stores top and bottom 5 |
| Authentication / RBAC | Single trusted user (HR Manager) |
| Audit trail | Single-user system; attribution is organizational |
| Backup / disaster recovery | Infrastructure concern |

## 4. Key Assumptions

- Single trusted user (HR Manager); no authentication
- Reports use USD as the reference currency
- Payroll amounts are entered, not computed
- Payroll periods are organization-wide, not per country
- Periods of the same frequency do not overlap
- An employee has at most one current assignment, always
- Payroll is recorded at period granularity; assignment changes take effect at
  the start of a payroll period
- Payroll summaries reference an assignment rather than duplicating placement
- For a given employee, payroll summaries do not cover overlapping date ranges.
  This is enforced by HR workflow, not by the system.
- ~8 countries, ~10 departments, ~15 roles, 10,000 employees seeded
  deterministically

## 5. Report Catalog

Each report is defined by its inputs, output shape, and source.

### Aggregate reports

Group payroll data over a date range by zero or more dimensions. Dimensions
available: country of employment, department, role, employment type, payroll
currency, country of origin. `totalSalary` is the measure, returned in the
reference currency.

| # | Report | Group keys | Extra |
| :--- | :--- | :--- | :--- |
| 1 | Total payroll for a range | none | |
| 2 | Payroll cost by country | country | |
| 3 | Payroll cost by department | department | |
| 4 | Payroll cost by role | role | |
| 5 | Payroll cost by employment type | employmentType | |
| 6 | Payroll cost by currency | currency | |
| 7 | Component breakdown | any | `includeBreakdown: true` |
| 9 | Min, max, average per dimension | any | `includeStatistics: true` |

**Output line:** amount in reference currency, employee count, and the group key
values that were requested. Report 7 additionally returns the five component
amounts. Report 9 additionally returns min, max, and average.

### Ranking report

| # | Report | Inputs | Source |
| :--- | :--- | :--- | :--- |
| 8 | Top and bottom earners | payroll period, country, department, role, direction | Snapshot rankings |

**Output line:** rank, employee name, amount in reference currency. Up to 5.

### Comparison report

| # | Report | Inputs | Source |
| :--- | :--- | :--- | :--- |
| 10 | Period comparison | two date ranges, group keys | Two aggregate computations |

**Output line:** group key values, both periods' amount and employee count, delta,
delta percent.

### Not supported

- Salary distribution buckets
- Top N beyond 5
- Any per-employee historical view

## 6. Success Criteria

### Functional

- All ten reports in the catalog can be produced
- Reports over closed periods read from snapshots; reports spanning open periods
  combine the most recent applicable snapshot with live data
- Snapshot totals match live aggregation computed at the same moment
- Payroll summaries cannot be edited once validated
- Payroll summaries cannot be created for closed periods
- Creating a new assignment ends the current one, atomically
- An assignment with payroll allows only its end date to be edited

### Performance

- The HR Manager can find any employee in under 5 seconds
- Any report over any date range returns in under 2 seconds with 10,000 employees
- Closing a payroll period, including snapshot computation, completes in under
  10 seconds

### Quality

- Incomplete data is surfaced as warnings, never silently ignored
- Reports that cannot be correct (missing FX rate) are blocked, not produced
- Tests cover domain logic, repositories, API contracts, snapshot consistency,
  and one end-to-end flow

### Deliverable

- The project builds, tests, and deploys with documented commands