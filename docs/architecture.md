# Architecture

> Status: 🚧 In progress — initial design documented.

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

- Record and manage employee payroll data
- Record assignments that describe where employees work over time
- Validate payroll summaries and close periods
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
installing or configuring a database server. The workload is read-heavy with a
single user, so SQLite's single-writer limitation is not a bottleneck.

In a production deployment serving a multi-user HR team, the database would be
PostgreSQL — for concurrent writes (MVCC), enforced constraints (SQLite does not
enforce `NOT NULL` / `FOREIGN KEY` by default), and richer analytical functions.

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

## Data Model

### Entities

| Entity | Purpose |
| :--- | :--- |
| **Country** | ISO code, name, currency code |
| **Department** | Canonical department list |
| **Role** | Canonical role list |
| **Employee** | Identity: name, email, birth date, origin country |
| **EmployeePeriodicAssignment** | Where and as what an employee worked, over a span of time |
| **PayrollPeriod** | Named date range with a frequency; a record-keeping unit |
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

Enums are used where the set of values is small, fixed, and known at design
time. They avoid reference tables and joins that would add no benefit at this
scale. Adding a value later is an enum extension plus a migration.

### Key relationships

- `EmployeePeriodicAssignment` → Employee, Country, Department, Role
- `PayrollSummary` → Employee, PayrollPeriod, EmployeePeriodicAssignment
- Placement (country, department, role) is read from the assignment
- `PayrollPeriodAggregate` → PayrollPeriod, Country, Department, Role
- `PayrollPeriodEarnerRanking` → PayrollPeriod, Country, Department, Role, Employee

### Key constraints

- `Country.isoCode` is unique
- `Department.name` and `Role.name` are unique
- `PayrollPeriod.name` is unique
- `PayrollPeriod.startDate` must be before `PayrollPeriod.endDate`
- `PayrollPeriod.frequency` is `MONTHLY` (28–31 days) or `BI_WEEKLY` (14 days)
- Periods of the same frequency must not overlap
- Payroll periods are organization-wide, not per country
- `PayrollSummary` is unique per `(employee, payroll period)`
- `PayrollSummary.total = base + variable + allowances + employerTaxes`
- `PayrollSummary.currency` matches the assignment's country currency
- `PayrollSummary`'s period falls entirely within the assignment's date range
- Validated payroll summaries are immutable
- Payroll summaries cannot be created for closed periods
- `EmployeePeriodicAssignment` ranges do not overlap for the same employee
- `PayrollPeriodAggregate` is unique per `(period, country, department, role)`
- `PayrollPeriodEarnerRanking` is unique per
  `(period, country, department, role, direction, rank)`

### Not enforced by the system

- For a given employee, payroll summaries do not cover overlapping date ranges.
  This is an HR workflow assumption, not a database constraint.
- Overlap prevention for payroll periods is enforced at the service layer.
  SQLite does not support exclusion constraints.

### Validation at save time

**EmployeePeriodicAssignment**
- Start before end (when end is present)
- No overlap with the same employee's other assignments

**PayrollSummary**
- `(employee, payroll period)` unique
- Components non-negative
- `total` equals the sum of components
- Assignment belongs to the same employee
- Period falls entirely within the assignment's date range
- Currency matches the assignment's country currency
- Period is not closed

**PayrollPeriod**
- Name unique
- Start before end
- Frequency matches duration (monthly 28–31 days, bi-weekly exactly 14 days)
- No overlap with another period of the same frequency

### Data quality at report time

- **Blockers:** missing FX rate for a currency used in the period; invalid date
  range; future period
- **Warnings:** employees with an active assignment but no validated payroll
  summary; partial country coverage

### Entry vs. reporting

Data entry and reporting apply different policies:

- **Entry** is strict. A payroll summary that contradicts an assignment is
  rejected. Closed periods reject new summaries.
- **Reporting** is lenient. Gaps in the data do not block the report; they are
  surfaced as warnings.

The distinction: entry is one record at a time and can be fixed immediately;
reporting queries thousands of records, and blocking the whole report for one
gap is worse than warning.

## Snapshot Design

When a payroll period is closed, the system computes and stores a snapshot. The
snapshot is a **checkpoint**, not a report.

### What a snapshot stores

**PayrollPeriodAggregate** — one row per observed `(country, department, role)`
combination within the period:

- `originalAmounts` — the five amounts in the country's currency
- `referenceAmounts` — the same five amounts in the reference currency
- `employeeCount`

**PayrollPeriodEarnerRanking** — up to 10 rows per combination (top 5 and
bottom 5):

- `direction` — TOP or BOTTOM
- `rank` — 1..5
- `employeeId`
- `amount` — total in reference currency

### Why these two tables

- **Aggregates** support every dimension-based report: by country, department,
  role, employment type (via assignment), currency, and component breakdown.
- **Rankings** support top/bottom earners and make min and max derivable: the
  maximum is the top of the TOP list; the minimum is the top of the BOTTOM list.

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

## Data Flow

**Save a payroll summary**
1. Client sends employee, payroll period, assignment, amounts
2. Backend validates structural rules (non-negative, total equals sum, uniqueness)
3. Backend verifies the assignment belongs to the employee and covers the period
4. Backend verifies the currency matches the assignment's country currency
5. Backend verifies the period is not closed
6. The summary is saved in `DRAFT` status

**Validate a payroll summary**
1. Client sends the summary ID
2. Backend transitions status from `DRAFT` to `VALIDATED`
3. The summary becomes immutable

**Close a payroll period**
1. Client sends the period ID
2. Backend verifies all summaries in the period are `VALIDATED`
3. Backend computes aggregates and rankings
4. Backend stores them and marks the period `closed`
5. Further writes to the period are rejected

**Generate a report**
1. Client sends a date range, dimensions, and a data-quality flag
2. Backend resolves the payroll periods within the range
3. Backend checks for blockers; if any, returns a blocked response
4. Backend starts from the latest applicable snapshot and adds live data for the
   remainder, converting using current rates
5. Returns data plus any warnings

## Cross-Cutting Concerns

- **Validation:** Bean Validation on DTOs; domain rules in services
- **Error handling:** a global exception handler maps domain exceptions to HTTP
  status codes and consistent error bodies
- **Logging:** SLF4J + Logback, Spring Boot defaults
- **Configuration:** environment-driven via `.env` in development; platform
  environment variables in production

## Deployment

Locally, the backend runs with `./gradlew bootRun` and the frontend with
`npm start` in the frontend folder. For production, `./gradlew bootJar` builds
the frontend, bundles it into the backend's static resources, and produces a
single executable JAR.

## Diagrams

Visual references will live in `docs/diagrams/` as they are created.
```
