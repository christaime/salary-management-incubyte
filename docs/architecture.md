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

In production, the database would be PostgreSQL — for concurrent writes,
enforced constraints, and richer analytical functions. The swap is a datasource
change, not a code change.

### Why a single deployable JAR

The frontend is compiled and bundled into the backend's static resources, so the
application ships as one artifact. This simplifies deployment and matches the
"fully functional deployed software" requirement.

## Backend Layers

| Layer | Responsibility |
| :--- | :--- |
| `domain` | Entities, value objects, domain rules |
| `application` | Services, use cases, orchestration |
| `infrastructure` | Persistence (JPA), Flyway, external concerns |
| `api` | REST controllers, DTOs, request/response mapping |

Dependencies flow inward: `api` → `application` → `domain`. Infrastructure
implements interfaces the domain and application define.

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
| **Department** | Canonical department list (stable `code` + display `name`) |
| **Role** | Canonical role list (stable `code` + display `name`) |
| **Employee** | Identity: matricule, name, email, birth date, origin country |
| **EmployeePeriodicAssignment** | Where and as what an employee worked, over a span of time |
| **PayrollPeriod** | Named date range with a frequency and a closed flag |
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
| **PayrollSummaryStatus** | `DRAFT`, `VALIDATED` |
| **EarnerRankingDirection** | `TOP`, `BOTTOM` |
| **GroupKey** | `COUNTRY`, `DEPARTMENT`, `ROLE`, `EMPLOYMENT_TYPE`, `CURRENCY`, `ORIGIN_COUNTRY` |
| **ReportKind** | `AGGREGATE`, `RANKING`, `COMPARISON` |

### Field detail — Employee

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| matricule | String | Unique, required |
| name | String | Required |
| email | String | Required |
| birthDate | LocalDate | Required |
| originCountry | Country | Nullable |

### Field detail — Department

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| code | String | Unique, required, 2..10 chars |
| name | String | Unique, required, 2..100 chars |

### Field detail — Role

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| code | String | Unique, required, 2..10 chars |
| name | String | Unique, required, 2..100 chars |

### Field detail — EmployeePeriodicAssignment

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| employeeId | UUID | Required |
| startDate | LocalDate | Required |
| endDate | LocalDate | Nullable |
| country | Country | Required |
| department | Department | Required |
| role | Role | Required |
| employmentType | EmploymentType | Required |
| inEffect | boolean | At most one `true` per employee |

### Field detail — PayrollPeriod

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| name | String | Unique |
| startDate | LocalDate | Required, before endDate |
| endDate | LocalDate | Required |
| frequency | PayrollPeriodFrequency | Required |
| closed | boolean | Default `false` |

### Field detail — PayrollSummary

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| employeeId | UUID | Required |
| payrollPeriod | PayrollPeriod | Required |
| employeePeriodicAssignmentId | UUID | Required |
| amounts | Amounts | Required |
| status | PayrollSummaryStatus | Default `DRAFT` |

### Field detail — PayrollPeriodAggregate

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| payrollPeriod | PayrollPeriod | Required |
| status | PayrollSummaryStatus | Required |
| country | Country | Required |
| department | Department | Required |
| role | Role | Required |
| originalAmounts | Amounts | Required |
| referenceAmounts | Amounts | Required |
| employeeCount | Integer | Required, non-negative |

Unique: `(payrollPeriod, country, department, role)`.

### Field detail — PayrollPeriodEarnerRanking

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| payrollPeriod | PayrollPeriod | Required |
| status | PayrollSummaryStatus | Required |
| country | Country | Required |
| department | Department | Required |
| role | Role | Required |
| direction | EarnerRankingDirection | Required |
| rank | Integer | 1..5 |
| employee | Employee | Required |
| amount | BigDecimal | Non-negative, reference currency |

Unique: `(payrollPeriod, country, department, role, direction, rank)`.

### Field detail — CurrencyRate

| Field | Type | Constraint |
| :--- | :--- | :--- |
| id | UUID | PK |
| currencyCode | String | Required |
| referenceCurrencyCode | String | Required |
| rate | BigDecimal | Required, positive |

Unique: `(currencyCode, referenceCurrencyCode)`.

### PayrollPeriodSnapshot — domain-only

`PayrollPeriodSnapshot` is **not persisted**. It is a transient view assembled
from the aggregates and rankings of a period for a given status. It has no
table, no repository, no id.

```
PayrollPeriodSnapshot
├── payrollPeriod
├── List<PayrollPeriodAggregate>
└── List<PayrollPeriodEarnerRanking>
```

The status lives on the aggregate and ranking rows, not on the snapshot. This
removes a join and lets the snapshot be assembled by a single filtered query
per table.

---

## Key Relationships

- `EmployeePeriodicAssignment` → Employee (UUID), Country, Department, Role
- `PayrollSummary` → Employee (UUID), PayrollPeriod, EmployeePeriodicAssignment (UUID)
- `PayrollPeriodAggregate` → PayrollPeriod, Country, Department, Role
- `PayrollPeriodEarnerRanking` → PayrollPeriod, Country, Department, Role, Employee

---

## Key Constraints

- `Country.isoCode` unique
- `Department.code` unique
- `Role.code` unique
- `Department.name` unique
- `Role.name` unique
- `Employee.matricule` unique
- `PayrollPeriod.name` unique
- `PayrollPeriod.startDate` before `PayrollPeriod.endDate`
- `PayrollPeriod.frequency` matches duration (monthly 28–31 days, bi-weekly 14)
- Periods of the same frequency do not overlap
- At most one `EmployeePeriodicAssignment.inEffect = true` per employee
- `EmployeePeriodicAssignment` ranges do not overlap per employee
- `PayrollSummary` unique per `(employee, payroll period)`
- `PayrollSummary.total = base + variable + allowances + employerTaxes`
- `PayrollSummary.currency` matches the assignment's country currency
- `PayrollSummary`'s period falls entirely within the assignment's date range
- Validated payroll summaries are immutable
- Payroll summaries cannot be created for closed periods
- `PayrollPeriodAggregate` unique per `(period, country, department, role)`
- `PayrollPeriodEarnerRanking` unique per `(period, country, department, role, direction, rank)`
- `CurrencyRate` unique per `(currency, reference currency)`

### Not enforced by the system

- For a given employee, payroll summaries do not cover overlapping date ranges.
- Overlap prevention for payroll periods is enforced in the service layer.
  SQLite does not support exclusion constraints.

---

## Validation at Save Time

**Employee**
- Matricule unique
- Required fields present
- Creation requires an assignment

**EmployeePeriodicAssignment**
- Start before end (when end is present)
- No overlap with the same employee's other assignments
- At most one `inEffect = true` per employee
- Creating a new assignment sets the previous one's `inEffect` to false

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
- Period is open

**Payroll recording — in-effect assignment check**
- The in-effect assignment's date range must cover the payroll period
- If it does not, recording cannot continue
- The system explains why and offers two ways to resolve it:
  - Extend the assignment's end date
  - Create a new assignment
- Recording continues once the assignment covers the period

---

## Data Quality at Report Time

- **Blockers:** missing FX rate for a currency used in the period; invalid date
  range; future period
- **Warnings:** employees with an in-effect assignment but no validated payroll
  summary; partial country coverage

### Entry vs. reporting

- **Entry** is strict. A payroll summary that contradicts an assignment is
  rejected. Closed periods reject new summaries. The reason is always explained.
- **Reporting** is lenient. Gaps in the data do not block the report; they are
  surfaced as warnings.

---

## Snapshot Design

A snapshot is the aggregation of a period: the totals and rankings, stored per
period and per status. The report reads snapshots.

### How snapshots are stored

`PayrollPeriodAggregate` and `PayrollPeriodEarnerRanking` rows carry both
`payrollPeriodId` and `status`. There is no snapshot table.

To read the snapshot for a period at a given status:

```
SELECT * FROM payroll_period_aggregate
WHERE payroll_period_id = ? AND status = ?
```

```
SELECT * FROM payroll_period_earner_ranking
WHERE payroll_period_id = ? AND status = ?
```

Two queries, no join. The status discriminator lets the report find either the
draft or the validated snapshot for a period.

### How the report builds snapshots

```
1. Resolve the payroll periods in the requested date range
2. Find the periods where closed = false
3. For each such period:
   build a new snapshot in memory (aggregates + rankings)
   if a blocker occurs:
   collect the blocker, do NOT touch the period's existing rows
   else:
   in one transaction: delete the period's rows, insert the new ones
4. If any blocker occurred:
   return the blockers to the user; the report is not produced
5. Otherwise:
   read all snapshots for periods in the range
   produce the report
```

**All-or-nothing:** the build phase is a single unit of work. If any period in
the range blocks, nothing is written.

**Always rebuild:** every non-closed period in the range is rebuilt on every
report. No draft is reused, because a fix for a blocker could affect any
non-validated snapshot.

**Build outside the transaction:** the build is read-only; the delete-and-insert
happens in one transaction after all builds succeed.

### Why per-period, not cumulative

Each snapshot holds the totals for its period only. A report over a range sums
the snapshots in the range. At this scale — tens of periods, hundreds of
combination rows per period — summing is trivial. Cumulative snapshots would
add complexity for no benefit.

### Snapshot growth

For each period, the aggregate table has one row per observed
`(country, department, role)` combination. With ~8 countries, ~10 departments,
and ~15 roles, the number of combinations that actually occur is bounded and
small. The ranking table adds up to 10 rows per combination. Over a year of
monthly and bi-weekly periods, this is on the order of thousands of rows —
negligible.

---

## Report Model

Reports fall into three kinds. Each has its own request and response shape.

### Group keys

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
groupKeys: GroupKey[],
includeBreakdown: boolean,
includeStatistics: boolean
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
amount: Amount,
employeeCount: number,
country?: string,
department?: string,
role?: string,
employmentType?: string,
currency?: string,
originCountry?: string,
breakdown?: Amounts,
statistics?: { min: Amount, max: Amount, average: Amount }
}
```

**Constraints**
- `statistics` requires `COUNTRY`, `DEPARTMENT`, and `ROLE` as group keys.
- Lines are sparse: only the requested group key values are populated.
- Empty `groupKeys` returns a single total line.

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
limit: number
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
- `limit` is capped at 5.
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
- A group present in only one period is reported with zero on the missing side.
- `deltaPercent` is `null` when `period1.amount` is zero.

### API endpoints

```
POST /api/reports/aggregate
POST /api/reports/ranking
POST /api/reports/comparison
```

Base path `/api/v1` is configured in `application.yml`.

---

## Data Flow

### Create an employee

1. Client sends identity fields and first assignment fields
2. Backend validates matricule uniqueness and assignment dates
3. Backend creates the Employee and the first assignment with `inEffect = true`
4. Both are saved atomically

### Create a new assignment

1. Client sends the new assignment fields and the current assignment's end date
2. Backend validates the new assignment's start date is after the current one's end date
3. Backend validates the current assignment's new end date still covers every
   payroll period that references it
4. Backend creates the new assignment with `inEffect = true` and sets the
   previous assignment's `inEffect = false`
5. All changes are atomic

### Save a payroll summary

1. Client sends employee, payroll period, amounts
2. Backend resolves the employee's in-effect assignment
3. Backend checks the assignment covers the payroll period
   - If not, the request fails with an explanation and fix options
4. Backend validates structural rules
5. Backend verifies the currency matches the assignment's country currency
6. Backend verifies the period is open
7. The summary is saved in `DRAFT` status

### Validate a payroll summary

1. Client sends the summary ID, or a period ID for bulk validation
2. Backend transitions status from `DRAFT` to `VALIDATED`
3. The summary becomes immutable

### Close a payroll period

1. Client requests the close
2. Backend verifies all summaries in the period are `VALIDATED`
3. Backend builds the snapshot: aggregates and rankings
4. Backend stores them (replacing any existing rows for the period)
5. The period is marked `closed = true`

### Generate a report

1. Client sends a request of one of the three kinds
2. Backend resolves the payroll periods within the date range
3. Backend rebuilds snapshots for every period where `closed = false`
   - If any blocker occurs, nothing is written and the report is not produced
4. Backend reads all snapshots for periods in the range
5. Returns data plus any warnings

---

## Cross-Cutting Concerns

- **Validation:** Bean Validation on DTOs; domain rules in services
- **Error handling:** a global exception handler maps domain exceptions to HTTP
  status codes and consistent error bodies. Blocked operations always explain
  why and what to do.
- **Logging:** SLF4J + Logback, Spring Boot defaults
- **Configuration:** environment-driven via `.env` in development; platform
  environment variables in production

---

## Deployment

Locally, the backend runs with `./gradlew bootRun` and the frontend with
`npm start` in the frontend folder. For production, `./gradlew bootJar` builds
the frontend, bundles it into the backend's static resources, and produces a
single executable JAR.

## Diagrams

Visual references will live in `docs/diagrams/` as they are created.
