
---

## Data Flow

### Create an employee

1. Client sends identity fields and first assignment fields
2. Backend validates matricule uniqueness and assignment dates
3. Backend creates the Employee and the first assignment with `inEffect = true`
4. Both are saved atomically

### Create a new assignment

1. Client sends the new assignment fields and the current assignment's end date
2. Backend validates the new assignment's start date is after the current one's
   end date
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
4. Backend validates structural rules (non-negative, total equals sum,
   uniqueness)
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
3. Backend computes aggregates and rankings
4. Backend stores them and sets `snapshotStatus` to `DRAFT`
5. Backend returns the snapshot and any data-quality issues
6. Client reviews and validates
7. Backend sets `snapshotStatus` to `VALIDATED`; the period is closed

### Generate a report

1. Client sends a request of one of the three kinds
2. Backend resolves the payroll periods within the date range
3. Backend checks for blockers; if any, returns a blocked response with reasons
4. Backend starts from the latest applicable snapshot and adds live data for the
   remainder, converting using current rates
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