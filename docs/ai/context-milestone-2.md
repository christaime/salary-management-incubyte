# Context — Salary Management, Milestone 2

You are continuing work on a Spring Boot 3.3 / Java 17 / Gradle / SQLite
project: a take-home assessment for Incubyte. Clean architecture, solo project.

## Working style

- File by file. Generate one file, wait for review, then the next.
- Provide a commit message after each logical group.
- No scope creep. No additions we did not ask for.
- English only.
- 100-char line width.
- Short Javadoc on every class.

## Architecture rules (non-negotiable)

- Domain in plain Java — no Spring, no JPA, no HTTP annotations.
- Repository ports live in the domain; implementations go in infrastructure.
- Domain objects are immutable, built with static inner `Builder` classes.
- Jakarta Bean Validation annotations on domain fields.
- Validation runs inside `build()` via `DomainValidation`.
- Mutations via `withX(...)` return new instances and re-validate.
- MapStruct for DTO and persistence mapping. Lombok `@Getter` on domain classes.
- UUID identity. No audit fields.
- No setters. No equals/hashCode.
- Package root: `com.salary.management`.

## Packages (established in Milestone 1)

| Concern | Package |
|---|---|
| Validation | `com.salary.management.common.validation` |
| Pagination | `com.salary.management.common.pagination` |
| Domain models | `com.salary.management.domain.model` |
| Enums | `com.salary.management.domain.model.enums` |
| Value objects | `com.salary.management.domain.model.values` |
| Repository ports | `com.salary.management.domain.repository` |
| Criteria | `com.salary.management.domain.repository.criteria` |
| JPA entities | `com.salary.management.infrastructure.persistence.entity` |
| Embeddables | `com.salary.management.infrastructure.persistence.embeddable` |
| Mappers | `com.salary.management.infrastructure.persistence.mapper` |
| JPA repositories | `com.salary.management.infrastructure.persistence.repository` |
| Adapters | `com.salary.management.infrastructure.persistence.adapter` |
| Adapter utils | `com.salary.management.infrastructure.persistence.adapter.utils` |
| Specifications | `com.salary.management.infrastructure.persistence.specification` |

## Milestone 1 — what exists

**Validation (3 files in `common/validation/`):**
- `ValidationError` — record: `(String field, String code, String message, Map<String,Object> attributes)`
- `DomainValidationException extends RuntimeException` — carries `List<ValidationError> getErrors()`
- `DomainValidation` — utility with `validate(T)`, `validate(T, List<ValidationError>)`, `toValidationError(ConstraintViolation)`, `isValid(T)`, `getViolations(T)`. Error codes come from the constraint annotation's simple name; cross-field codes are hand-assigned (`DateOrder`, `TotalMismatch`).

**Enums (6 in `domain/model/enums/`):**
`EmploymentType`, `PayrollPeriodFrequency`, `PayrollSummaryStatus`, `EarnerRankingDirection`, `GroupKey`, `ReportKind`.

**Value object (1 in `domain/model/values/`):**
`Amounts` — base, variable, allowances, employerTaxes, total, currency. Builder computes total from the four components; if a total is provided and differs, `ValidationError` code `TotalMismatch`.

**Domain models (11 in `domain/model/`):**
`Country`, `Department`, `Role`, `Employee`, `CurrencyRate`,
`EmployeePeriodicAssignment`, `PayrollPeriod`, `PayrollSummary`,
`PayrollPeriodSnapshot`, `PayrollPeriodAggregate`,
`PayrollPeriodEarnerRanking`.

Foreign reference rule (IMPORTANT):
- References to `Country`, `Department`, `Role`, `PayrollPeriod` → hold the **domain object** in the domain class.
- References to aggregates (`Employee`, `EmployeePeriodicAssignment`, `PayrollSummary`) → stay as **`UUID`**.
- `Employee.originCountry` is nullable.

**Repository ports (11 in `domain/repository/`):**
`CountryRepository`, `DepartmentRepository`, `RoleRepository`,
`EmployeeRepository`, `CurrencyRateRepository`,
`EmployeePeriodicAssignmentRepository`, `PayrollPeriodRepository`,
`PayrollSummaryRepository`, `PayrollPeriodSnapshotRepository`,
`PayrollPeriodAggregateRepository`, `PayrollPeriodEarnerRankingRepository`.

**Tests (12 in `src/test/java/com/salary/management/domain/model/`):**
One per domain model, JUnit 5, `@DisplayName` on every test,
`assertThrows(DomainValidationException.class, ...)`.

## Design decisions made for Milestone 2

### Repository port shape

Every repository has:
- `save(...)`, `findById(UUID)`, `deleteById(UUID)` — commands and lookups
- `list(Criteria)` → `List<T>`
- `page(Criteria)` → `DataPage<T>`

No `search(...)`, no `findAll()`, no listing without criteria.

Known-key lookups like `findByIsoCode(String)`, `existsByName(String)`,
`findByMatricule(String)` remain as-is.

### Criteria contract

Every `<Entity>Criteria` class implements `com.salary.management.common.pagination.Criteria`:

```java
public interface Criteria {
    String sortBy();                     // requested field, or null
    SortDirection sortDirection();       // requested direction, or null
    Set<String> sortableFields();        // allow-list
    String defaultSortField();           // used when missing or invalid
    SortDirection defaultSortDirection(); // direction for the default field
    int page();
    int size();
}
```

Criteria live in `domain/repository/criteria/`. Each has a builder.

### Pagination types (in `common/pagination/`)

- `SortDirection` — enum: `ASC`, `DESC`
- `Criteria` — the interface above
- `DataPage<T>` — a record: `(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last, boolean empty)`
    - Compact constructor computes `totalPages`, `first`, `last`, `empty`
    - Static `empty()`
    - `map(Function<T,R>)` for DTO conversion
    - `numberOfElements()`, `hasContent()`

No `Sort` wrapper class. No `Sort.Order`. No `PageRequest` wrapper — the criteria carries page and size directly.

### CriteriaUtils (in `infrastructure/persistence/adapter/utils/`)

```java
public final class CriteriaUtils {
    private CriteriaUtils() {}

    public static org.springframework.data.domain.Sort toSpringSort(Criteria criteria) {
        String field = criteria.sortBy();
        SortDirection direction = criteria.sortDirection();

        if (field == null || !criteria.sortableFields().contains(field)) {
            return Sort.by(
                toSpringDirection(criteria.defaultSortDirection()),
                criteria.defaultSortField()
            );
        }
        return Sort.by(
            toSpringDirection(direction != null ? direction : SortDirection.ASC),
            field
        );
    }

    public static Pageable toPageable(Criteria criteria) {
        return PageRequest.of(criteria.page(), criteria.size(), toSpringSort(criteria));
    }

    private static Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.DESC
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    }
}
```

Sort field validation happens here. Unknown field falls back to the default.
No exceptions thrown.

### JPA entities

- Entities hold **UUID columns only** for foreign references — no `@ManyToOne`, no object references, no lazy loading.
- `@Id UUID id` with `@GeneratedValue(strategy = GenerationType.UUID)`.
- The domain generates the UUID in its `build()`, so the entity always has an ID when saved. Hibernate treats it as assigned.
- `@Enumerated(EnumType.STRING)` for enums.
- `Amounts` maps to an `@Embeddable AmountsEmbeddable` with its six fields.
- No audit columns.

### Mappers

Domain ↔ entity mapping via MapStruct. Signature pattern:

```java
PayrollSummary toDomain(PayrollSummaryEntity entity, PayrollPeriod period);
PayrollSummaryEntity toEntity(PayrollSummary summary);
```

`toDomain` receives the already-loaded domain objects for foreign references. The adapter loads them explicitly.

`toEntity` is simpler — the domain already carries UUIDs of its references.

### Adapters

Adapters implement the domain ports. For read operations that need foreign
references, the adapter injects the corresponding JPA repositories, loads the
referenced entities, and passes the resolved domain objects to the mapper.

Example pattern:

```java
@Override
public Optional<PayrollSummary> findById(UUID id) {
    return jpaRepository.findById(id).map(entity -> {
        PayrollPeriod period = payrollPeriodJpaRepository
            .findById(entity.getPayrollPeriodId())
            .map(payrollPeriodMapper::toDomain)
            .orElseThrow(() -> new IllegalStateException(
                "PayrollPeriod not found: " + entity.getPayrollPeriodId()));
        return mapper.toDomain(entity, period);
    });
}
```

For `page(criteria)` and `list(criteria)`:

```java
@Override
public DataPage<PayrollSummary> page(PayrollSummaryCriteria criteria) {
    Specification<PayrollSummaryEntity> spec = PayrollSummarySpecification.build(criteria);
    Pageable pageable = CriteriaUtils.toPageable(criteria);
    Page<PayrollSummaryEntity> page = jpaRepository.findAll(spec, pageable);
    return toDataPage(page);   // loads references, maps, and wraps
}

@Override
public List<PayrollSummary> list(PayrollSummaryCriteria criteria) {
    Specification<PayrollSummaryEntity> spec = PayrollSummarySpecification.build(criteria);
    Sort sort = CriteriaUtils.toSpringSort(criteria);
    List<PayrollSummaryEntity> entities = jpaRepository.findAll(spec, sort);
    return entities.stream()
        .map(this::toDomain)     // loads references
        .toList();
}
```

### Specifications

One `<Entity>Specification` per criteria. Static `build(Criteria)` returning
`Specification<Entity>`. Builds predicates from the criteria's filters.

## Milestone 2 scope

Deliver the entire persistence layer. Nothing above it (no services, no DTOs,
no controllers).

### Sequence

| # | Deliverable | Commit message |
|---|---|---|
| 2.1 | `SortDirection`, `Criteria`, `DataPage<T>` in `common/pagination/` | `Milestone 2: add pagination types` |
| 2.2 | `CriteriaUtils` in `infrastructure/persistence/adapter/utils/` | `Milestone 2: add criteria utils` |
| 2.3 | `AmountsEmbeddable` + 11 JPA entities | `Milestone 2: add JPA entities` |
| 2.4 | Flyway migration `V1__initial_schema.sql` | `Milestone 2: add initial schema migration` |
| 2.5 | 11 MapStruct persistence mappers | `Milestone 2: add persistence mappers` |
| 2.6 | 11 Spring Data JPA repositories | `Milestone 2: add JPA repositories` |
| 2.7 | 11 repository adapters + criteria classes | `Milestone 2: add repository adapters` |
| 2.8 | Specifications (one per criteria) | `Milestone 2: add specifications` |
| 2.9 | Adapter integration tests | `Milestone 2: add adapter integration tests` |

### Gradle dependencies to confirm

`spring-boot-starter-data-jpa`, `sqlite-jdbc`, `hibernate-community-dialects`,
`flyway-core`, `org.mapstruct:mapstruct` + `mapstruct-processor`,
`lombok` + `lombok-mapstruct-binding` (annotation processor order matters:
Lombok first, then MapStruct).

### Configuration

`application.yml` already has the SQLite datasource, Flyway settings,
`ddl-auto: validate`, and Actuator. No changes needed for Milestone 2 beyond
what already exists.

## What comes after Milestone 2

Milestones 3–7 build toward Slice 1 (an aggregate report over seeded data):
- M3 — Seed script
- M4 — Snapshot builder
- M5 — Aggregate report backend (DTOs, service, controller)
- M6 — Aggregate report frontend (Angular)
- M7 — Package slice 1

## Current working directory

The repo is `salary-management-incubyte`. Backend is in `backend/`. Frontend
in `frontend/`.

## What to do first

Start with Step 2.1: `SortDirection`, `Criteria`, `DataPage<T>` in
`common/pagination/`. Generate one file, wait for review, then the next.
After the three files, provide the commit message.

Do not proceed to 2.2 until told.
