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
  the HR Manager.** The system does not generate it.
- **Creating an employee requires defining the employee's first assignment.**
  An employee without an assignment has no placement and cannot be used for
  payroll.
- Search by matricule, name, email, or placement (country, department, role,
  employment type)
- The employee list shows identity plus the in-effect placement
- Server-side pagination
- **Deletion is allowed only if the employee has no payroll, including drafts.**
  Deleting an employee also deletes their assignments.

**Rationale — why Employee holds no placement.**
An employee's country, department, and role are historical facts, not properties
of the person. They change over time. Modeling them on Employee would force a
single value or require per-employee versioning. Instead, placement is recorded
on assignments, which are naturally dated.

**Rationale — why the employee list shows in-effect placement.**
The HR Manager uses the list to find people and to see where they work. Showing
the in-effect assignment makes the list useful at a glance.

### Employee assignments

- An **EmployeePeriodicAssignment** records where an employee worked and in what
  role, over a span of time
- Fields: employee, start date, end date (nullable), country, department, role,
  employment type (enum), and an `inEffect` flag
- Assignments for the same employee must not overlap
- **Exactly one assignment per employee is marked `inEffect`.** The flag is a
  declaration by the HR Manager: it says which assignment applies.
- **Creating a new assignment flips the flag.** The current in-effect assignment
  becomes non-in-effect, and the new one becomes in-effect. This is a single
  atomic operation.
- **Editing rules:**
    - If the assignment has no payroll: all fields are editable
    - If the assignment has payroll: only the end date is editable, and the new
      end date must still cover every payroll period that references the assignment

**Rationale — why assignments are a distinct concept.**
Payroll depends on where and how an employee was placed. Tracking placement over
time allows payroll records to reference what was actually true during the
period.

**Rationale — why the `inEffect` flag exists.**
The flag marks the assignment that applies. Without it, determining which
assignment is "the one" would require a date comparison on every read. The flag
makes it a stored fact, declared by the HR.

**Rationale — why the flag is a declaration, not a date derivation.**
The HR Manager decides which assignment applies. The system records that
decision. When the HR moves through payroll periods, they keep the in-effect
assignment aligned with the period being processed. If the flag and the dates
disagree, the disagreement surfaces during payroll recording, where it can be
resolved in place.

**Employment type values.**
Employment type is an enum with three values: `FULL_TIME`, `PART_TIME`, and
`CONTRACTOR`. The set is small, fixed, and known at design time. Modeling it as
an enum avoids a reference table.

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
    - The period must be open
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
records what was actually paid, in the currency it was paid in.

**Rationale — why the draft/validated lifecycle exists.**
Payroll data is entered over time. A draft state lets the HR Manager record and
revise entries without exposing them to reports. Validating is the deliberate
act that declares a summary final. Corrections after validation are out of
scope.

### Payroll list

- Filtered by a **date range**, not by a specific payroll period
- Filters: employee, role, department, country, employment type, date range
- Columns: employee, payroll period, country, department, role, employment type,
  total and currency
- Server-side pagination

**Rationale — why the list filters by date range rather than by period.**
The HR Manager often wants to look across periods. Filtering by date range
matches how reports work and removes a step from the common query.

### Reference data

- **Country** — ISO code, name, currency code
- **Department** — id, name (canonical list)
- **Role** — id, name (canonical list)
- **PayrollPeriod** — name, start date, end date, frequency, snapshot status
  (`NOT_COMPUTED`, `DRAFT`, `VALIDATED`)
- **CurrencyRate** — the current rate per currency pair against the reference
  currency

**Rationale — why departments and roles are canonical lists.**
In the spreadsheet, department and role names are free text, so grouping is
unreliable. Enforcing a fixed list ensures every payroll record uses a canonical
value and aggregations are exact.

**Rationale — why payroll periods are organization-wide.**
A single HR Manager manages payroll across countries. Periods are shared, not
per-country.

**Rationale — why periods carry a frequency and may overlap across frequencies.**
Different groups of employees may be paid on different cycles. Periods of the
same frequency must not overlap; periods of different frequencies may.

**Rationale — why currency rates are keyed by currency, not country.**
An exchange rate describes a currency pair, not a country.

**Rationale — why currency rates are not historical.**
Once a payroll period's snapshot is validated, its converted amounts are frozen.
Reports over closed periods never re-convert.

### Payroll period management

- Create a period with a frequency, start date, and end date. A name is
  generated from the dates.
- Overlap with a period of the same frequency is rejected.
- **Recording payroll for a new period requires that the previous period of the
  same frequency is closed.** If it is not, the system offers to close it first.
- **A period can be edited or deleted only if it has no payroll.**
- **Closing a period is a two-step process.** The system computes the snapshot
  in `DRAFT`, presents it to the HR Manager along with data-quality issues, and
  the HR Manager validates it. Validation closes the period.
- The period list shows each period with its status.

**Rationale — why the previous period must be closed.**
The snapshot is cumulative. A report starting from a closed period's snapshot
assumes every earlier period is also closed. Requiring the previous period to be
closed preserves the checkpoint chain. It also ensures the HR Manager confirms
the previous period is complete before starting the next one.

**Rationale — why closing requires reviewing the snapshot.**
The snapshot is the historical record. Validating it is the moment the HR
Manager accepts that the period's data is final. Presenting it makes that
acceptance deliberate.

### Reporting

- Reports are defined by a date range and aggregate all payroll periods within it
- All figures are presented in a single reference currency (USD)
- Reports fall into three kinds:
    - **Aggregate** — totals grouped by zero or more dimensions
    - **Ranking** — the top or bottom earners within a specific period and group
    - **Comparison** — two aggregate reports side by side
- Reports support a data-quality check:
    - **Blockers** prevent report generation (missing FX rate, invalid range,
      future period)
    - **Warnings** allow the report to be produced and state how the system
      handled the issue

**Rationale — why reports are not persisted.**
A report is a query, not a record. Generating on demand keeps the answer
current.

**Rationale — why some conditions block and others warn.**
Some conditions make the report impossible to produce correctly. Others make it
incomplete but still meaningful. Blocking in the first case prevents wrong
numbers; warning in the second prevents dead ends.

### Data

- Deterministic seed script generating 10,000 employees across ~8 countries and
  ~10 departments, with assignments and several periods of payroll summaries
- Assignments are generated first; payroll summaries are derived from them

## 3. Deliberately Out of Scope

| Excluded | Reason |
| :--- | :--- |
| Payroll execution (tax computation, net pay, payslips) | Legally jurisdiction-specific; the brief asks for salary data and reporting |
| Payroll corrections after validation | Validated summaries are immutable; correcting them would require an audit trail |
| Itemized salary components | A single amount per component is sufficient for reporting |
| Multiple concurrent assignments per employee | Would require a join table between employee, period, and assignment |
| Historical exchange rates | Closed periods freeze their conversions |
| Excel import | A deterministic seed script replaces data onboarding |
| PDF / Excel export | The UI is the presentation layer |
| Salary distribution buckets | Requires individual salary values |
| Top N beyond 5 | The snapshot stores top and bottom 5 |
| Authentication / RBAC | Single trusted user |
| Audit trail | Single-user system; attribution is organizational |
| Backup / disaster recovery | Infrastructure concern |

## 4. Key Assumptions

- Single trusted user (HR Manager); no authentication
- Reports use USD as the reference currency
- Payroll amounts are entered, not computed
- Payroll periods are organization-wide, not per country
- Periods of the same frequency do not overlap
- An employee has exactly one in-effect assignment
- The `inEffect` flag is a declaration by the HR, not a date derivation
- Payroll summaries reference an assignment rather than duplicating placement
- For a given employee, payroll summaries do not cover overlapping date ranges
- ~8 countries, ~10 departments, ~15 roles, 10,000 employees seeded
  deterministically

## 5. Report Catalog

### Aggregate reports

Group payroll data over a date range by zero or more dimensions. Dimensions:
country of employment, department, role, employment type, payroll currency,
country of origin. `totalSalary` is the measure, in the reference currency.

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

### Ranking report

| # | Report | Inputs |
| :--- | :--- | :--- |
| 8 | Top and bottom earners | payroll period, country, department, role, direction |

### Comparison report

| # | Report | Inputs |
| :--- | :--- | :--- |
| 10 | Period comparison | two date ranges, group keys |

### Not supported

- Salary distribution buckets
- Top N beyond 5
- Per-employee historical views

## 6. Success Criteria

### Functional

- All ten reports can be produced
- Reports over closed periods read from snapshots; reports spanning open periods
  combine the most recent applicable snapshot with live data
- Snapshot totals match live aggregation computed at the same moment
- Payroll summaries cannot be edited once validated
- Payroll summaries cannot be created for closed periods
- Creating a new assignment flips the in-effect flag, atomically
- An assignment with payroll allows only its end date to be edited
- Payroll recording cannot proceed when the in-effect assignment does not cover
  the period, and the reason is explained

### Performance

- The HR Manager can find any employee in under 5 seconds
- Any report over any date range returns in under 2 seconds with 10,000 employees
- Closing a payroll period completes in under 10 seconds

### Quality

- Incomplete data is surfaced, never silently ignored
- Reports that cannot be correct are blocked, not produced
- Tests cover domain logic, repositories, API contracts, snapshot consistency,
  and one end-to-end flow

### Deliverable

- The project builds, tests, and deploys with documented commands