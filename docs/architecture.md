# Architecture

> Status: 🚧 In progress — design documented.

## Overview

Salary Management is a web application with a Java backend and an Angular
frontend, packaged as a single deployable JAR. The backend exposes a REST API and
serves the compiled frontend. Data is stored in SQLite.

```
┌─────────────────┐      HTTP/REST      ┌──────────────────────────┐
│  Angular UI     │ ◄─────────────────► │  Spring Boot API (Java)  │
└─────────────────┘                     └────────────┬─────────────┘
│
▼
┌──────────────────────┐
│   SQLite (file DB)   │
└──────────────────────┘
```

## System Context

The system has one user: the HR Manager. They use it to:

- Record employees and their assignments
- Record payroll summaries
- Validate payroll and close payroll periods
- Generate reports about how the organization pays people

There is no authentication; the application assumes a single trusted user and
that infrastructure restricts access.

## Technology Stack

| Concern | Choice | Rationale |
| :--- | :--- | :--- |
| Backend language | Java 17 | Matches role requirement |
| Backend framework | Spring Boot 3.3 | Mature ecosystem; matches role requirement |
| Build tool | Gradle | Matches role requirement |
| Database | SQLite | Zero-config for reviewers; sufficient for a read-heavy single-user system |
| Schema management | Flyway | Migration-driven schema; `ddl-auto: validate` ensures Hibernate never alters the schema |
| Frontend | Angular 19 | Standalone components, signals, OnPush |
| UI library | Angular Material + Tailwind | Material for complex widgets; Tailwind for layout and custom components |
| Testing (backend) | JUnit 5 + Mockito | Standard for Spring Boot |
| Testing (frontend) | Jasmine / Karma | Standard for Angular |
| CI | GitHub Actions | Tests on every push |

### Why SQLite

SQLite is zero-config: a reviewer can clone the repository and run it without
installing a database server. The workload is read-heavy with a single user, so
SQLite's single-writer limitation is not a bottleneck.

In production, the database would be PostgreSQL — for concurrent writes, enforced
constraints, and richer analytical functions. The swap is a datasource change,
not a code change.

### Why a single deployable JAR

The frontend is compiled and bundled into the backend's static resources, so the
application ships as one artifact.

## Backend Layers

| Layer | Responsibility |
| :--- | :--- |
| `domain` | Entities, value objects, domain rules |
| `application` | Services, use cases, orchestration |
| `infrastructure` | Persistence (JPA), Flyway, external concerns |
| `api` | REST controllers, DTOs, request/response mapping |

Dependencies flow inward: `api` → `application` → `domain`.

## Frontend Structure

| Folder | Responsibility |
| :--- | :--- |
| `core/` | Services, models, interceptors |
| `features/` | Feature modules (employees, assignments, payroll, periods, reports) |
| `shared/` | Reusable components, pipes, directives |

State is managed with Angular signals. Components are standalone and use OnPush
change detection.

---

## Data Model

### Entities

| Entity | Purpose |
| :--- | :--- |
| **Country** | ISO code, name, currency code |
| **Department** | Canonical department list |
| **Role** | Canonical role list |
| **Employee** | Identity: matricule, name, email, birth date, origin country |
| **EmployeePeriodicAssignment** | Where and as what an employee worked, over a span of time |
| **PayrollPeriod** | Named date range with a frequency and a snapshot status |
| **PayrollSummary** | Payroll record for an employee in a period, linked to an assignment |
| **CurrencyRate** | Current exchange rate for a currency pair |
| **PayrollPeriodAggregate** | Snapshot of totals by (period, country, department, role) |
| **PayrollPeriodEarnerRanking** | Snapshot of top and bottom earners by the same key |

### Value object

| Object | Fields |
| :--- | :--- |
| **Amounts** | base, variable, allowances, employerTaxes, total, currency |

`Amounts` is used by `PayrollSummary` (once) and `PayrollPeriodAggregate` (twice:
`originalAmounts` and `referenceAmounts`).

### Enums

| Enum | Values |
| :--- | :--- |
| **EmploymentType** | `FULL_TIME`, `PART_TIME`, `CONTRACTOR` |
| **PayrollPeriodFrequency** | `MONTHLY`, `BI_WEEKLY` |
| **PayrollPeriodStatus** | `NOT_COMPUTED`, `DRAFT`, `VALIDATED` |
| **PayrollSummaryStatus** | `DRAFT`, `VALIDATED` |
| **EarnerRankingDirection** | `TOP`, `BOTTOM` |
| **GroupKey** | `COUNTRY`, `DEPARTMENT`, `ROLE`, `EMPLOYMENT_TYPE`, `CURRENCY`, `ORIGIN_COUNTRY` |
| **ReportKind** | `AGGREGATE`, `RANKING`, `COMPARISON` |

### Field detail — Employee

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| matricule | String | Unique across the organization, required, entered by the HR |
| name | String | Required |
| email | String | Required |
| birthDate | LocalDate | Required |
| originCountryId | UUID | FK → Country, required |

### Field detail — EmployeePeriodicAssignment

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| employeeId | UUID | FK → Employee, required |
| startDate | LocalDate | Required |
| endDate | LocalDate | Nullable |
| countryId | UUID | FK → Country, required |
| departmentId | UUID | FK → Department, required |
| roleId | UUID | FK → Role, required |
| employmentType | EmploymentType | Required |
| current | boolean | At most one `true` per employee |

### Field detail — PayrollPeriod

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| name | String | Unique |
| startDate | LocalDate | Required, before endDate |
| endDate | LocalDate | Required |
| frequency | PayrollPeriodFrequency | Required |
| snapshotStatus | PayrollPeriodStatus | Default `NOT_COMPUTED` |

### Field detail — PayrollSummary

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| employeeId | UUID | FK → Employee, required |
| payrollPeriodId | UUID | FK → PayrollPeriod, required |
| employeePeriodicAssignmentId | UUID | FK → EmployeePeriodicAssignment, required |
| amounts | Amounts | Required |
| status | PayrollSummaryStatus | Default `DRAFT` |

### Field detail — PayrollPeriodAggregate

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| payrollPeriodId | UUID | FK → PayrollPeriod, required |
| countryId | UUID | FK → Country, required |
| departmentId | UUID | FK → Department, required |
| roleId | UUID | FK → Role, required |
| originalAmounts | Amounts | Required |
| referenceAmounts | Amounts | Required |
| employeeCount | int | Required |

Unique: `(payrollPeriodId, countryId, departmentId, roleId)`.

### Field detail — PayrollPeriodEarnerRanking

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| payrollPeriodId | UUID | FK → PayrollPeriod, required |
| countryId | UUID | FK → Country, required |
| departmentId | UUID | FK → Department, required |
| roleId | UUID | FK → Role, required |
| direction | EarnerRankingDirection | Required |
| rank | int | 1..5 |
| employeeId | UUID | FK → Employee, required |
| amount | BigDecimal | In reference currency |

Unique: `(payrollPeriodId, countryId, departmentId, roleId, direction, rank)`.

### Field detail — CurrencyRate

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| currencyCode | String | Required |
| referenceCurrencyCode | String | Required |
| rate | BigDecimal | Required |

Unique: `(currencyCode, referenceCurrencyCode)`.

---

## Key Relationships

- `EmployeePeriodicAssignment` → Employee, Country, Department, Role
- `PayrollSummary` → Employee, PayrollPeriod, EmployeePeriodicAssignment
- Placement (country, department, role) is read from the assignment
- `PayrollPeriodAggregate` → PayrollPeriod, Country, Department, Role
- `PayrollPeriodEarnerRanking` → PayrollPeriod, Country, Department, Role, Employee

---

## Key Constraints

- `Country.isoCode` unique
- `Department.name` and `Role.name` unique
- `Employee.matricule` unique
- `PayrollPeriod.name` unique
- `PayrollPeriod.startDate` before `PayrollPeriod.endDate`
- `PayrollPeriod.frequency` matches duration (monthly 28–31 days, bi-weekly 14)
- Periods of the same frequency do not overlap
- At most one `EmployeePeriodicAssignment.current = true` per employee
- `EmployeePeriodicAssignment` ranges do not overlap for the same employee
- `PayrollSummary` unique per `(employee, payroll period)`
- `PayrollSummary.total = base + variable + allowances + employerTaxes`
- `PayrollSummary.currency` matches the assignment's country currency
- `PayrollSummary`'s period falls entirely within the assignment's date range
- Validated payroll summaries are immutable
- Payroll summaries cannot be created for closed periods
- `PayrollPeriodAggregate` unique per `(period, country, department, role)`
- `PayrollPeriodEarnerRanking` unique per
  `(period, country, department, role, direction, rank)`
- `CurrencyRate` unique per `(currency, reference currency)`

### Not enforced by the system

- For a given employee, payroll summaries do not cover overlapping date ranges.
  This is an HR workflow assumption.
- Overlap prevention for payroll periods is enforced in the service layer.
  SQLite does not support exclusion constraints.

---

## Validation at Save Time

**Employee**
- Matricule unique
- Required fields present
- Creation requires an assignment (enforced by the create-employee flow)

**EmployeePeriodicAssignment**
- Start before end (when end is present)
- No overlap with the same employee's other assignments
- At most one current assignment per employee

**PayrollPeriod**
- Name unique
- Start before end
- Frequency matches duration
- No overlap with another period of the same frequency
- Editable or deletable only when no payroll exists

**PayrollSummary**
- `(employee, payroll period)` unique
- Components non-negative
- Total equals the sum of components
- Assignment belongs to the same employee
- Period falls entirely within the assignment's date range
- Currency matches the assignment's country currency
- Period is not closed

---

## Data Quality at Report Time

- **Blockers:** missing FX rate for a currency used in the period; invalid date
  range; future period
- **Warnings:** employees with an active assignment but no validated payroll
  summary; partial country coverage

### Entry vs. reporting

- **Entry** is strict. A payroll summary that contradicts an assignment is
  rejected. Closed periods reject new summaries.
- **Reporting** is lenient. Gaps in the data do not block the report; they are
  surfaced as warnings.

---

## Snapshot Design

When a period closes, the system computes and stores a snapshot. The snapshot is
a **checkpoint**, not a report.

### What a snapshot stores

**PayrollPeriodAggregate** — one row per observed
`(country, department, role)` combination within the period:

- `originalAmounts` — the five amounts in the country's currency
- `referenceAmounts` — the same five amounts in the reference currency
- `employeeCount`

**PayrollPeriodEarnerRanking** — up to 10 rows per combination (top 5 and
bottom 5):

- `direction` — TOP or BOTTOM
- `rank` — 1..5
- `employeeId`
- `amount` — total in reference currency

### Cumulative totals

Snapshots store cumulative totals — from the beginning of recorded payroll
through the end of their period. A report over any range starts from the latest
applicable snapshot and adds live data for the remainder. This mirrors how an
account balance works, and it reduces report cost: a report over closed years
reads a few thousand pre-aggregated rows instead of scanning 10,000 employees'
payroll summaries across every period.

### Snapshot growth

For each period, the aggregate table has one row per observed combination. With
~8 countries, ~10 departments, and ~15 roles, the number of combinations that
actually occur is bounded and small. The ranking table adds up to 10 rows per
combination. Over a year of monthly and bi-weekly periods, this is on the order
of thousands of rows — negligible.

---

## Report Model

Reports fall into three kinds. Each has its own request and response shape.

### Group keys

The available group keys are:

| Key | Source |
| :--- | :--- |
| `COUNTRY` | Assignment → Country |
| `DEPARTMENT` | Assignment → Department |
| `ROLE` | Assignment → Role |
| `EMPLOYMENT_TYPE` | Assignment → EmploymentType |
| `CURRENCY` | PayrollSummary.currency |
| `ORIGIN_COUNTRY` | Employee → Country |

`totalSalary` is not a group key. It is the measure returned by every aggregate
report, in the reference currency.

### Shared types

**DateRange**
```
{ startDate: LocalDate, endDate: LocalDate }
```

**Amount**
```
{ amount: BigDecimal, currency: String }
```

**ReportWarning**
```
{
code: string,
severity: BLOCKER | WARNING,
message: string,
details?: object
}
```

### Type 1 — Aggregate report

**Request**
```
{
kind: "AGGREGATE",
dateRange: DateRange,
groupKeys: GroupKey[],          // may be empty
includeBreakdown: boolean,      // default false
includeStatistics: boolean      // default false
}
```

**Response**
```
{
kind: "AGGREGATE",
dateRange: DateRange,
groupKeys: GroupKey[],
referenceCurrency: string,
lines: [ AggregateLine ],
warnings: ReportWarning[]
}
```

**AggregateLine**
```
{
amount: Amount,                  // reference currency
employeeCount: number,
country?: string,
department?: string,
role?: string,
employmentType?: string,
currency?: string,
originCountry?: string,
breakdown?: Amounts,             // only when includeBreakdown
statistics?: {                   // only when includeStatistics
min: Amount,
max: Amount,
average: Amount
}
}
```

**Constraints**
- `statistics` requires the group keys to include `COUNTRY`, `DEPARTMENT`, and
  `ROLE`, so the line can join with the ranking snapshot. Otherwise the API
  returns a validation error.
- Lines are sparse: only the requested group key values are populated.
- When `groupKeys` is empty, a single line is returned with the total.

### Type 2 — Ranking report

**Request**
```
{
kind: "RANKING",
payrollPeriodId: UUID,
country: string,
department: string,
role: string,
direction: "TOP" | "BOTTOM",
limit: number                    // default 5, max 5
}
```

**Response**
```
{
kind: "RANKING",
payrollPeriodId: UUID,
country: string,
department: string,
role: string,
direction: "TOP" | "BOTTOM",
referenceCurrency: string,
lines: [ EarnerLine ]
}
```

**EarnerLine**
```
{
rank: number,
employeeName: string,
amount: Amount
}
```

**Constraints**
- `limit` is capped at 5. Requesting more returns a validation error.
- The report is period-scoped, not range-scoped.

### Type 3 — Comparison report

**Request**
```
{
kind: "COMPARISON",
dateRange1: DateRange,
dateRange2: DateRange,
groupKeys: GroupKey[]
}
```

**Response**
```
{
kind: "COMPARISON",
groupKeys: GroupKey[],
referenceCurrency: string,
lines: [ ComparisonLine ],
warnings: ReportWarning[]
}
```

**ComparisonLine**
```
{
country?: string,
department?: string,
role?: string,
employmentType?: string,
currency?: string,
originCountry?: string,
period1: { amount: Amount, employeeCount: number },
period2: { amount: Amount, employeeCount: number },
delta: Amount,
deltaPercent: number | null
}
```

**Constraints**
- A group value present in only one period is reported with zero on the missing
  side — the group is not dropped.
- `deltaPercent` is `null` when `period1.amount` is zero.

### API endpoints

```
POST /api/reports/aggregate
POST /api/reports/ranking
POST /api/reports/comparison
```

Each endpoint validates its request and returns its response type.

---

## Data Flow

### Create an employee

1. Client sends identity fields and first assignment fields
2. Backend validates matricule uniqueness and assignment dates
3. Backend creates the Employee and the first assignment (with `current = true`)
4. Both are saved atomically

### Create a new assignment for an existing employee

1. Client sends new assignment fields and the end date for the current one
2. Backend validates the new assignment's start date is after the current one's
   end date
3. Backend validates the current assignment's new end date still covers every
   payroll period that references it
4. Backend sets the current assignment's end date, marks it non-current, creates
   the new assignment as current
5. All three changes are atomic

### Save a payroll summary

1. Client sends employee, payroll period, assignment, amounts
2. Backend validates structural rules (non-negative, total equals sum, uniqueness)
3. Backend verifies the assignment belongs to the employee and covers the period
4. Backend verifies the currency matches the assignment's country currency
5. Backend verifies the period is not closed
6. The summary is saved in `DRAFT` status

### Validate a payroll summary

1. Client sends the summary ID, or a period ID for bulk validation
2. Backend transitions status from `DRAFT` to `VALIDATED`
3. The summary becomes immutable

### Close a payroll period

1. Client requests the close
2. Backend verifies all summaries in the period are `VALIDATED`
3. Backend computes aggregates and rankings
4. Backend stores them and sets the period's `snapshotStatus` to `DRAFT`
5. Backend returns the snapshot and any warnings
6. Client reviews and validates
7. Backend sets `snapshotStatus` to `VALIDATED`; the period is now closed

### Generate a report

1. Client sends a request of one of the three kinds
2. Backend resolves the payroll periods within the date range
3. Backend checks for blockers; if any, returns a blocked response
4. Backend starts from the latest applicable snapshot and adds live data for the
   remainder, converting using current rates
5. Returns data plus any warnings

---

## Cross-Cutting Concerns

- **Validation:** Bean Validation on DTOs; domain rules in services
- **Error handling:** a global exception handler maps domain exceptions to HTTP
  status codes and consistent error bodies
- **Logging:** SLF4J + Logback, Spring Boot defaults
- **Configuration:** environment-driven via `.env` in development; platform
  environment variables in production

---

## Deployment

Locally, the backend runs with `./gradlew bootRun` and the frontend with
`npm start`. For production, `./gradlew bootJar` builds the frontend, bundles it
into the backend's static resources, and produces a single executable JAR.

## Diagrams

Visual references will live in `docs/diagrams/` as they are created.
