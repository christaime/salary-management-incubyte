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
- Fields: name, email, birth date, origin country
- Search by name, email, origin country
- Server-side pagination

**Rationale — why Employee holds no placement.**
An employee's country, department, and role are historical facts, not properties
of the person. They change over time. Modeling them on Employee would either
force a single "current" value (losing history) or require per-employee
versioning (complex). Instead, placement is recorded on assignments, which are
naturally dated.

### Employee assignments

- An **EmployeePeriodicAssignment** records where an employee worked and in what
  role, over a span of time
- Fields: employee, start date, end date (nullable), country, department, role,
  employment type (enum)
- Assignments for the same employee must not overlap

**Rationale — why assignments are a distinct concept.**
Payroll depends on where and how an employee was placed. Tracking placement over
time allows payroll records to reference what was actually true during the
period, rather than trusting free-form data entry. It also gives reports a
stable dimension: "payroll cost by department" reads from the assignment, not
from a field someone typed.

**Rationale — why employment type lives on the assignment.**
Employment type is a property of the placement, not of the person — the same
individual may change type over time. Placing it on the assignment keeps the
model consistent: everything that can change over time lives on the assignment.

**Employment type values.**
Employment type is an enum with three values: `FULL_TIME`, `PART_TIME`, and
`CONTRACTOR`. The set is small, fixed, and known at design time, and no metadata
beyond the name is needed. Modeling it as an enum avoids a reference table and a
join that would add no benefit at this scale. Adding a category later means
extending the enum and running a migration — a small, deliberate change.

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

**Rationale — why payroll references an assignment instead of duplicating
placement.**
If payroll carried its own country, department, and role, the system could
record a payroll that contradicts the assignment. By referencing the assignment,
that contradiction becomes impossible — consistency is structural, not enforced
by validation alone.

**Rationale — why currency is stored on the payroll summary.**
The currency is a fact about the payment, not a property of the placement. It
records what was actually paid, in the currency it was paid in. It is validated
against the assignment's country on save, but not re-derived on read.

**Rationale — why the draft/validated lifecycle exists.**
Payroll data is entered over time and may be incomplete while being assembled.
A draft state lets the HR Manager record and revise entries without exposing them
to reports. Validating a summary is the deliberate act that declares it final.
Corrections after validation are out of scope — matching how final payroll is
treated operationally.

### Reference data

- **Country** — ISO code, name, currency code
- **Department** — id, name (canonical list)
- **Role** — id, name (canonical list)
- **PayrollPeriod** — name, start date, end date, frequency, closed flag
- **CurrencyRate** — the current rate per currency pair against the reference
  currency

**Rationale — why departments and roles are canonical lists.**
In the spreadsheet, department and role names are free text, so grouping is
unreliable ("Eng." vs "Engineering"). Enforcing a fixed list ensures that every
payroll record uses a canonical value and aggregations are exact.

**Rationale — why payroll periods are organization-wide.**
A single HR Manager manages payroll across countries. Periods are shared, not
per-country. This keeps reporting simple: one set of periods, aggregated across
all countries.

**Rationale — why periods carry a frequency and may overlap across frequencies.**
Different groups of employees may be paid on different cycles. Periods of the
same frequency must not overlap; periods of different frequencies may, by design.

**Rationale — why currency rates are keyed by currency, not country.**
An exchange rate describes a currency pair, not a country. Keying by currency
allows one rate to apply to every country that uses that currency, and allows
reports in different reference currencies without schema changes.

**Rationale — why currency rates are not historical.**
Once a payroll period closes, its reporting snapshot stores the converted
amounts. Reports over closed periods never re-convert. Historical rate precision
is therefore unnecessary — the rate table holds current values only.

### Period closing and snapshots

- When all summaries in a period are validated, the HR Manager may close the period
- Closing blocks further writes to the period
- Closing computes and stores a **reporting snapshot** for the period
- Reports over closed periods read the snapshot; reports over open periods read
  live data

**Rationale — why snapshots exist.**
They serve two purposes. First, they freeze the converted values, removing the
need for historical exchange rates. Second, they pre-aggregate the data that
reports need, so historical reports do not read thousands of payroll rows.

**Rationale — why the snapshot is a checkpoint, not a report.**
Snapshots store cumulative totals from the beginning of recorded payroll through
the end of their period. A report over any range starts from the latest
applicable snapshot and adds live data for the remainder. This mirrors how an
account balance works: start from the last known balance, apply subsequent
transactions. The effect on performance is significant: a report over three
closed years reads a few thousand pre-aggregated rows, rather than scanning and
grouping the payroll summaries of 10,000 employees across every period. Without
the checkpoint, each report would re-scan the full history.

### Reporting

- Reports are defined by a date range and aggregate all payroll periods within it
- All figures are presented in a single reference currency (USD)
- Reports support a data-quality check:
    - **Blockers** prevent report generation (missing FX rate, invalid range,
      future period)
    - **Warnings** allow the report to be produced and state how the system handled
      the issue (employees with an active assignment but no validated summary,
      partial country coverage)

**Rationale — why reports are not persisted.**
A report is a query, not a record. Persisting it would raise questions about
staleness and versioning. Generating on demand keeps the answer current.

**Rationale — why some conditions block and others warn.**
Some conditions make the report impossible to produce correctly (missing FX rate
means conversion cannot happen). Others make it incomplete but still meaningful.
Blocking in the first case prevents wrong numbers; warning in the second prevents
dead ends.

### Data

- Deterministic seed script generating 10,000 employees across ~8 countries and
  ~10 departments, with assignments and several periods of payroll summaries
- Assignments are generated first; payroll summaries are derived from them, so
  consistency is structural

**Rationale — why a seed script rather than Excel import.**
The assessment asks for a seed script, and importing from Excel would require
column mapping, validation, error reporting, and partial-failure semantics — a
feature in its own right. Seeding with consistent synthetic data demonstrates
the system without conflating it with data onboarding.

### Why this is better than the spreadsheet

- Payroll has an explicit draft/validated lifecycle; a spreadsheet has no notion
  of "final".
- Incomplete data is detected and reported, rather than producing a plausible
  but wrong number.

## 3. Deliberately Out of Scope

| Excluded | Reason |
| :--- | :--- |
| Payroll execution (tax computation, net pay, payslips) | Legally jurisdiction-specific; the brief asks for salary data and reporting, not payroll processing |
| Payroll corrections after validation | Validated summaries are immutable; correcting them would require an audit trail, which is also out of scope |
| Itemized salary components | A single amount per component is sufficient for reporting |
| Multiple concurrent assignments per employee | Would require a join table between employee, period, and assignment |
| Mid-period assignment changes | Payroll is recorded per period; assignment changes take effect at period boundaries. This matches how HR operations work in practice. |
| Assignment exceptions | Payroll and assignment must be consistent. The system rejects contradictions rather than tolerating them. |
| Historical exchange rates | Closed periods freeze their conversions; open periods use current rates |
| Excel import | A deterministic seed script replaces data onboarding for this assessment |
| PDF / Excel export | The UI is the presentation layer; export is a follow-on feature |
| Salary distribution buckets | Requires individual salary values; the snapshot stores totals and rankings |
| Top 10 earners | The snapshot stores top and bottom 5 |
| Authentication / RBAC | Single trusted user (HR Manager); infrastructure restricts access |
| Audit trail | Single-user system; attribution is organizational, not system-tracked |
| Backup / disaster recovery | Infrastructure concern, not application logic |

## 4. Key Assumptions

- Single trusted user (HR Manager); no authentication
- Reports use USD as the reference currency
- Payroll amounts are entered, not computed
- Payroll periods are organization-wide, not per country
- Periods of the same frequency do not overlap
- An employee has at most one assignment per payroll period
- Payroll is recorded at period granularity; assignment changes take effect at
  the start of a payroll period
- A payroll summary references an assignment rather than duplicating placement
  data — assignment consistency is structural
- For a given employee, payroll summaries do not cover overlapping date ranges.
  This is enforced by HR workflow, not by the system.
- Departments and roles are a fixed, managed list
- ~8 countries, ~10 departments, ~15 roles, 10,000 employees seeded deterministically

**Rationale — why "one trusted user" is an acceptable assumption.**
The brief names the HR Manager as the user. Modeling roles and permissions would
add a security surface without changing what the system demonstrates. The
assumption is explicit so it can be revisited.

## 5. Report Catalog

Each report is defined by its inputs, its output shape, and its source.

### Report 1 — Total payroll for a date range

- **Inputs:** start date, end date
- **Output:** a single amount in reference currency
- **Source:** snapshot + live delta
- **Answers:** *"What did we spend on payroll between X and Y?"*

### Report 2 — Payroll cost by country

- **Inputs:** start date, end date
- **Output:** one row per country: `{ country, totalReference, employeeCount, sharePercent }`
- **Source:** snapshot + live delta
- **Answers:** *"Where did the money go, geographically?"*

### Report 3 — Payroll cost by department

- **Inputs:** start date, end date
- **Output:** one row per department: `{ department, totalReference, employeeCount, sharePercent }`
- **Source:** snapshot + live delta
- **Answers:** *"Which functions consume the most payroll?"*

### Report 4 — Payroll cost by role

- **Inputs:** start date, end date
- **Output:** one row per role: `{ role, totalReference, employeeCount, sharePercent }`
- **Source:** snapshot + live delta
- **Answers:** *"Where in the hierarchy is the money concentrated?"*

### Report 5 — Payroll cost by employment type

- **Inputs:** start date, end date
- **Output:** one row per type: `{ employmentType, totalReference, employeeCount, sharePercent }`
- **Source:** snapshot + live delta
- **Answers:** *"What is our split of full-time, part-time, and contractors?"*

### Report 6 — Payroll cost by currency

- **Inputs:** start date, end date
- **Output:** one row per original currency: `{ currency, originalTotal, referenceTotal }`
- **Source:** snapshot + live delta
- **Answers:** *"What currencies do we pay in, and how much in each?"*

### Report 7 — Component breakdown

- **Inputs:** start date, end date, optional dimension (country, department, role, employment type)
- **Output:** for each group: `{ group, base, variable, allowances, employerTaxes, total }` — all in reference currency
- **Source:** snapshot + live delta
- **Answers:** *"How much of our payroll is fixed salary versus variable and benefits?"*

### Report 8 — Top and bottom earners

- **Inputs:** payroll period, country, department, role
- **Output:** up to 5 highest and 5 lowest earners: `{ employeeName, amountReference, rank }`
- **Source:** snapshot (closed periods) or live (open periods)
- **Answers:** *"Who are the highest and lowest paid people in this group?"*

### Report 9 — Min, max, and average compensation per dimension

- **Inputs:** start date, end date, dimension (country, department, role, or a combination)
- **Output:** one row per group: `{ group, min, max, average, employeeCount }`
- **Source:** snapshot + live delta
- **Answers:** *"How equal or unequal is pay within each group?"*

### Report 10 — Period comparison

- **Inputs:** two date ranges, optional dimension
- **Output:** side-by-side totals with delta and percentage change
- **Source:** two snapshots or two computations
- **Answers:** *"How did payroll change between these two periods?"*

### Not supported

- Salary distribution buckets — requires individual salary values, which the
  snapshot does not store
- Top N beyond 5 — the snapshot stores only the top and bottom 5
- Any per-employee historical view — individual history is out of scope

## 6. Success Criteria

### Functional

- All ten reports in the catalog can be produced
- Reports over closed periods read from snapshots; reports spanning open periods
  combine the most recent applicable snapshot with live data
- Snapshot totals match live aggregation computed at the same moment
- Payroll summaries cannot be edited once validated
- Payroll summaries cannot be created for closed periods

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