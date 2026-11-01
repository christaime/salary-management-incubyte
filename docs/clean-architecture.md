# Clean Architecture in This Project

A reference document describing how the layers are organized, what each layer
owns, and the rules that keep the boundaries intact. Written for a reader who
wants to understand the structure of the codebase, or apply it to a new
project.

---

## 1. Layering

The backend is organized in four concentric layers. Dependencies point **inward
only**. The domain knows nothing about Spring, JPA, HTTP, or the database.

```
┌─────────────────────────────────────────────────────┐
│  Infrastructure  (adapters, JPA, mappers)           │  ← outer
│  ┌───────────────────────────────────────────────┐  │
│  │  Application  (services, orchestration)       │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │  Domain  (models, value objects,        │  │  │
│  │  │           repository ports)             │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**Domain.** Pure Java. Contains:

- Domain models under `domain/model/` — `Country`, `Department`, `Role`,
  `Employee`, `CurrencyRate`, `EmployeePeriodicAssignment`, `PayrollPeriod`,
  `PayrollSummary`, `PayrollPeriodAggregate`, `PayrollPeriodEarnerRanking`,
  `PayrollPeriodEarnerPercentile`.
- Value objects under `domain/model/values/` — `Amount`, `Amounts`,
  `AmountUtils`, `GroupedSummary`, `RankedSummary`, `ReportWarning`,
  `SnapshotBuildResult`, `PayrollPeriodSnapshotResult`,
  `RankingAndPercentileSnapshot`.
- Enums under `domain/model/enums/` — `EmploymentType`,
  `PayrollPeriodFrequency`, `PayrollSummaryStatus`, `EarnerRankingDirection`,
  `GroupKey`, `ReportKind`, `ReportWarningCode`.
- Repository ports under `domain/repository/` — `CountryRepository`,
  `EmployeeRepository`, `PayrollSummaryRepository`, etc.
- Criteria classes under `domain/repository/criteria/` — one per searchable
  entity, plus `ReportEffectiveFilters`.
- Projections under `domain/repository/projection/` —
  `AggregateRowProjection`.

Domain owns:

- Business invariants expressed as Bean Validation constraints on fields.
- Cross-field rules checked in the builder's `build()` method.
- Repository **interfaces** — `CountryRepository`, `EmployeeRepository`, etc.
  These describe what the domain needs from persistence, in domain language
  (`findByIsoCode`, `existsByMatricule`), not in ORM language.

**Application.** Use cases and orchestration, under `application/service/`. One
service class per concern — `CountryService`, `DepartmentService`,
`RoleService`, `EmployeeService`, `PayrollPeriodService`,
`PayrollSummaryService`, `PayrollSummaryValidator`, `AggregateReportService`,
`RankingReportService`. Supporting packages: `application/dto/` holds request
and response shapes grouped by feature (`aggregate/`, `employee/`, `payroll/`,
`ranking/`), `application/mapper/` holds API-layer MapStruct mappers,
`application/exception/` holds application-level exceptions such as
`ReportBlockedException`.

**Infrastructure.** Everything that talks to the outside world, under
`infrastructure/`:

- `api/` — controllers, HTTP error handling in `api/advice/`, CORS and
  OpenAPI config in `api/config/`. Controllers are `CountryController`,
  `DepartmentController`, `EmployeeController`, `PayrollPeriodController`,
  `PayrollSummaryController`, `ReportController`, `RoleController`.
- `persistence/` — JPA entities (`entity/`), MapStruct persistence mappers
  (`mapper/`), repository adapters (`adapter/`), Spring Data JPA repositories
  (`repository/`), specifications (`specification/`), embeddables
  (`embeddable/`), projections (`projection/`), the
  `LocalDateStringConverter` (`converter/`).
- `database/` — `SqliteDirectoryInitializer`, which ensures the SQLite file
  directory exists before Flyway runs.

**Why the domain does not see JPA.** The `EmployeePeriodicAssignment` domain
model carries `Country`, `Department`, and `Role` as domain objects. The
`EmployeePeriodicAssignmentEntity` carries `countryId`, `departmentId`,
`roleId` as UUIDs. The adapter resolves them and calls
`mapper.toDomain(entity, country, department, role)`. This is why the domain
can enforce "an assignment always has a country, department, and role" without
JPA relations, and why the adapter has to load them explicitly.

**Why controllers live under `infrastructure/api/`.** Controllers are adapters
— they adapt HTTP requests to application service calls. Placing them under
`infrastructure/api/` keeps the sibling-package rule: everything that imports
a framework lives under `infrastructure/`.

---

## 2. Repositories as ports

Every domain repository interface is a port. Adapters are the implementations.
The application layer depends on the interface, never on `JpaRepository`.

```
domain.repository.EmployeeRepository                          ← interface (port)
infrastructure.persistence.adapter.EmployeeRepositoryAdapter  ← implementation
infrastructure.persistence.repository.EmployeeJpaRepository   ← Spring Data, used only by the adapter
```

Consequences:

- Tests can mock the port without the ORM.
- Swapping persistence is a new adapter, not a rewrite.
- The port speaks the domain's language: `findByMatricule`,
  `existsByIsoCode`, not `findAll(Sort)`.

One adapter, `PayrollPeriodEarnerRankingJdbcRepository`, uses JDBC directly
rather than Spring Data JPA. The earner-ranking query is a windowed rank that
the Criteria API cannot express cleanly. It lives beside the JPA
implementation of the same port, and both satisfy the same domain interface.

---

## 3. The repository port shape

Every port has the same shape:

```java
public interface XRepository {
    X save(X x);
    Optional<X> findById(UUID id);
    void deleteById(UUID id);
    List<X> list(XCriteria criteria);
    DataPage<X> page(XCriteria criteria);
    // plus known-key lookups: findByCode, existsByName, etc.
}
```

There is no `findAll()`. A list without criteria goes through
`list(XCriteria.builder().build())`. This forces every query through the
criteria pattern so filtering and sorting are always available.

The ports do not expose `count()`. If a count is needed, it goes through
`page(criteria).totalElements()`.

---

## 4. Criteria and pagination

Every searchable entity has a `<X>Criteria` class under
`domain/repository/criteria/` implementing a common `Criteria` interface
(`common/pagination/Criteria`):

```java
public interface Criteria {
    String sortBy();
    SortDirection sortDirection();
    Set<String> sortableFields();
    String defaultSortField();
    SortDirection defaultSortDirection();
    int page();
    int size();
}
```

Each criteria class:

- Declares its own `SORTABLE_FIELDS` allow-list.
- Declares its own default sort field and direction.
- Carries optional filters as fields.
- Has a builder.

Sort fields are validated against the allow-list by
`infrastructure/persistence/adapter/utils/CriteriaUtils`. An unknown field
falls back to the default sort — it never throws and never leaks a column
name.

`DataPage<T>` (`common/pagination/DataPage`) wraps a page of results: content,
page number, size, total elements, plus derived `totalPages`, `first`, `last`,
`isEmpty`.

---

## 5. Specifications

The `<X>Specification` class under
`infrastructure/persistence/specification/` builds a JPA
`Specification<XEntity>` from the criteria:

```java
public static Specification<XEntity> build(XCriteria criteria) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        // add predicates from criteria filters
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

The specification is the single place where filter semantics live. Changing
how a filter works means changing one method, not every adapter.

For filters that span entities (e.g. filtering payroll summaries by the
employee's assignment), the specification uses subqueries or joins, or relies
on denormalized columns on the entity.

The aggregate report uses `DynamicStatisticsBuilder` and
`DynamicRankingBuilder` under `infrastructure/persistence/repository/` to
assemble SQL dynamically from the request's group keys. These are a different
kind of builder — dynamic read-model queries, not specifications — and they
live next to the JPA repositories they support.

---

## 6. Persistence mapping

**No JPA relations, with one documented exception.** Every entity holds
foreign references as UUID columns. No `@ManyToOne`, no `@OneToMany`, no lazy
loading. This makes the persistence layer predictable and the adapters explicit
about what they load.

The exception is `PayrollSummaryEntity`, which uses `@ManyToOne` for
`employee` and `payrollPeriod` because the payroll list needs to sort and
filter on their columns without a join per row. The trade-off is documented in
the class.

**Mappers are MapStruct interfaces.** For simple entities, one method:

```java
@Mapper(componentModel = "spring")
public interface CountryPersistenceMapper {
    CountryEntity toEntity(Country country);
    Country toDomain(CountryEntity entity);
    List<Country> toDomainList(List<CountryEntity> entities);
}
```

For entities with foreign references, `toDomain` takes the resolved domain
objects as parameters:

```java
PayrollSummary toDomain(PayrollSummaryEntity entity, PayrollPeriod period);
```

The adapter loads the referenced entities and passes them. `toEntity` uses the
UUIDs already present in the domain object.

**Why not hand-written mappers.** MapStruct generates the mapping code at
compile time, catches missing fields, and produces readable generated sources.
For straightforward field-to-field mappings, it is faster to write and less
error-prone than manual mapping. When the mapping is not straightforward
(foreign references, computed fields), the signature carries the extra
information and MapStruct follows it.

---

## 7. Domain immutability

Every domain object is **immutable**. Fields are `final`. Mutations happen
through `withX(...)` methods that return a new instance.

```java
public PayrollSummary withStatus(PayrollSummaryStatus newStatus) {
    return toBuilder().status(newStatus).build();
}
```

No setters. No `setX(...)`. Ever.

**Why.**

- Invariants are checked once, at construction. There is no `setX` to bypass
  validation later.
- Thread safety for free.
- Value semantics — two `Country` objects with the same fields are
  interchangeable.
- Signals intent — a `withX` call means state changed on purpose, and the
  change is traceable.

---

## 8. Builders with validation on `build()`

The pattern:

```java
Country country = Country.builder()
    .isoCode("FR")
    .name("France")
    .currencyCode("EUR")
    .build();
```

And `build()` fails if the object would be incoherent.

**What `build()` validates.**

- **Required fields.** No null isoCode, no missing currency.
- **Length and format.** Name within the column's length, email in a valid
  shape.
- **Cross-field rules.** A `PayrollPeriod` must have `startDate < endDate`. An
  `Amounts` total must equal the sum of components.
- **Enum coherence.** `employmentType` is an `EmploymentType`, not a string.

**How validation works.** Fields carry Jakarta Bean Validation annotations:

```java
@NotBlank
@Size(min = 2, max = 3)
private final String isoCode;
```

The builder's `build()` calls `DomainValidation.validate(object)` or
`DomainValidation.validate(object, extraErrors)` (`common/validation/`). The
helper runs Bean Validation, collects violations, merges any cross-field
errors the builder supplied, sorts them by field, and throws
`DomainValidationException` if the list is non-empty.

**Why not validation in setters, or in the entity, or in the service.**

- **Setters are bypassed by partial construction.** Builders accumulate, then
  validate once.
- **Entity validation is persistence-shape validation.** The entity knows
  about columns, not about business rules.
- **Service validation duplicates across every use case.** A rule like "total
  equals the sum of components" belongs on the domain object. Every service
  that constructs one gets it for free.

**The `build()` contract.**

- Throws `DomainValidationException` on invalid input.
- The exception carries structured `ValidationError` records.
- Never returns a partial object, never returns null.
- If a field is optional, the builder either leaves it null or substitutes a
  documented default (e.g. `CurrencyRate.referenceAmount` defaults to
  `BigDecimal.ONE`).

**Transitions re-validate.** `withStatus(...)` runs the same validation as
`build()` on the changed state, then returns a new instance. You cannot
produce an invalid `PayrollSummary` by any sequence of transitions.

---

## 9. Structured validation errors

`DomainValidationException` carries a `List<ValidationError>`
(`common/validation/ValidationError`):

```java
public record ValidationError(
    String field,
    String code,
    String message,
    Map<String, Object> attributes
) {}
```

- `field` — the property path, e.g. `"dateRange.startDate"`
- `code` — the constraint's simple name (`NotBlank`, `Size`, `NotNull`,
  `DateOrder`, `TotalMismatch`)
- `message` — the default message from the annotation
- `attributes` — the constraint's attributes (`min`, `max`, `expected`,
  `actual`)

The `GlobalExceptionHandler` (`infrastructure/api/advice/`) maps
`DomainValidationException` to a 400 with the `errors` array intact. The
frontend maps errors by `field` and translates by `code`. The `attributes` are
available for interpolation.

This is why error codes are stable strings and not localized messages: the
frontend owns localization, the backend owns structure.

---

## 10. Testing strategy aligned with layers

- **Domain tests.** Plain JUnit, no Spring context. Instantiate domain objects,
  assert invariants, check transitions. Fast and deterministic. Live under
  `src/test/java/.../domain/model/`.
- **Adapter integration tests.** `@SpringBootTest` with a real SQLite database
  (test profile disables the seed). Each adapter gets its own test class under
  `src/test/java/.../infrastructure/persistence/adapter/`. The test seed is
  loaded from `src/test/resources/sql/`. Tests use `@Transactional` and roll
  back.
- **Application tests.** Use case tests under
  `src/test/java/.../application/service/` with mocked repository ports —
  `AggregateReportServiceTest`, `EmployeeServiceTest`,
  `PayrollSummaryServiceTest`, `RankingReportServiceTest`.
- **Controller tests.** `@WebMvcTest` with mocked application services —
  `PayrollSummaryControllerTest`, `ReportControllerTest`.

The adapter tests are the ones that matter most. They are the boundary between
the domain and the database, and they are where specifications, sorting, and
mapping logic live. A regression there corrupts data or silently drops filters,
and no UI test catches it.

---

## 11. Package structure

```
com.salary.management
├── SalaryManagementApplication.java
├── common
│   ├── exception               ConflictException, ResourceNotFoundException
│   ├── pagination              Criteria, DataPage, SortDirection
│   └── validation              DomainValidation, DomainValidationException,
│                               ValidationError
├── domain
│   ├── model
│   │   ├── Country, CurrencyRate, Department, Employee,
│   │   │   EmployeePeriodicAssignment, PayrollPeriod,
│   │   │   PayrollPeriodAggregate, PayrollPeriodEarnerPercentile,
│   │   │   PayrollPeriodEarnerRanking, PayrollSummary, Role
│   │   ├── enums               EarnerRankingDirection, EmploymentType,
│   │   │                       GroupKey, PayrollPeriodFrequency,
│   │   │                       PayrollSummaryStatus, ReportKind,
│   │   │                       ReportWarningCode
│   │   └── values              Amount, Amounts, AmountUtils, GroupedSummary,
│   │                           PayrollPeriodSnapshotResult, RankedSummary,
│   │                           RankingAndPercentileSnapshot, ReportWarning,
│   │                           SnapshotBuildResult
│   └── repository
│       ├── criteria            XCriteria classes, ReportEffectiveFilters
│       ├── projection          AggregateRowProjection
│       └── XRepository interfaces
├── application
│   ├── dto
│   │   ├── aggregate           AggregateLineDto, AggregateReportRequest,
│   │   │                       AggregateReportResponse, StatisticsDto,
│   │   │                       StatisticsPerFrequency
│   │   ├── employee            EmployeeAssignmentDto,
│   │   │                       EmployeeAssignmentSaveRequest,
│   │   │                       EmployeeAssignmentToCloseRequest,
│   │   │                       EmployeeRefDto, EmployeeSaveRequest,
│   │   │                       EmployeeWithInEffectAssignmentDto
│   │   ├── payroll             PayrollAssignmentDto, PayrollPeriodDto,
│   │   │                       PayrollSummaryCreateRequest,
│   │   │                       PayrollSummaryDetailDto, PayrollSummaryDto,
│   │   │                       PayrollSummarySearchRequest,
│   │   │                       PayrollSummaryUpdateRequest,
│   │   │                       PayrollSummaryValidateRequest,
│   │   │                       PeriodicAssignmentDto, ValidateAllResult
│   │   ├── ranking             EarnerRankingDto, EarningRankingReportRequest,
│   │   │                       EarningRankingReportResponse,
│   │   │                       PositionContextDto
│   │   └── AmountDto, AmountsDto, CountryResponse, DateRange,
│   │       PayrollPeriodResponse, RefDto, ReferenceDataResponse
│   ├── exception               ReportBlockedException
│   ├── mapper                  CountryApiMapper, EmployeeApiMapper,
│   │                           PayrollPeriodApiMapper, PayrollSummaryApiMapper,
│   │                           ReferenceDataApiMapper
│   └── service                 AggregateReportService, CountryService,
│                               DepartmentService, EmployeeService,
│                               PayrollPeriodService, PayrollSummaryService,
│                               PayrollSummaryValidator, RankingReportService,
│                               RoleService
└── infrastructure
    ├── api
    │   ├── advice              ApiError, ErrorResponse, GlobalExceptionHandler
    │   ├── config              CorsConfig, OpenApiConfig, WebConfig
    │   └── *Controller         Country, Department, Employee, PayrollPeriod,
    │                           PayrollSummary, Report, Role
    ├── database                SqliteDirectoryInitializer
    └── persistence
        ├── adapter
        │   ├── *RepositoryAdapter
        │   └── utils           CriteriaUtils, DataIntegrityMessages
        ├── converter           LocalDateStringConverter
        ├── embeddable          AmountEmbeddable, AmountsEmbeddable
        ├── entity              *Entity classes
        ├── mapper              *PersistenceMapper interfaces
        ├── projection          AggregateRow, PayrollAggregateRow, RankedRow,
        │                       StatisticsRow, SummaryValidationRow
        ├── repository          *JpaRepository, DynamicRankingBuilder,
        │                       DynamicStatisticsBuilder,
        │                       PayrollPeriodEarnerRankingJdbcRepository
        └── specification       *Specification classes
```

---

## 12. The two rules that keep this from drifting

1. **Dependencies point inward.** If a domain class imports something from
   `infrastructure` or `api`, the layering has failed.
2. **Every domain object is valid by construction.** If you can hold a
   reference, it is coherent. The only way to reach an invalid state is to
   bypass the domain — and the domain's job is to make that impossible for the
   code around it.

Everything else follows from these. The builders, the transitions, the
port/adapter split, the specification pattern — they are mechanical
consequences of "inner owns the rule" and "the object is always valid."

---

## 13. What carries to a new Spring Boot project

**Layering.**

- Domain in plain Java. No Spring annotations, no JPA annotations.
- Repository interfaces in the domain; implementations in infrastructure.
- Mappers generated by MapStruct, tested at the adapter level.
- Specifications build queries from criteria objects, not raw strings.
- Sort allow-lists per search request. Unknown sort field falls through to a
  default.

**Domain.**

- Immutable objects. Builder for construction, `withX` for transitions, no
  setters.
- Validate on `build()`, re-validate on every transition.
- Structured validation errors — field + code + message + attributes.
- Value objects for things like `Amounts`, `ReportWarning`. Put their rules
  there, not in the service.
- The domain never sees UUID references as objects. It sees the objects it
  needs, or it sees UUIDs when it does not need the object.

**Application.**

- One use case class per concern. Owns the transaction boundary.
- Application exceptions are distinct from domain exceptions. Map to HTTP in
  the controller advice, not in the domain.

**Infrastructure.**

- Adapters implement the domain ports.
- JPA entities are separate from domain models. Do not annotate the domain.
- Specifications for query composition; native SQL or JDBC for read models
  that cannot be expressed with Criteria.
- Adapter integration tests with a real database. These are the highest-value
  tests.

**API layer.**

- Request DTOs validated with Bean Validation annotations.
- Responses are DTOs, never entities or domain objects.
- Error responses have a stable shape: code, message, errors, timestamp.
- Never leak domain exceptions through to the HTTP client without translation.

**Testing.**

- Domain: fast unit tests, no context.
- Application: mocked ports.
- Adapters: real DB.
- Controllers: `@WebMvcTest` with mocked services.
- One smoke test that boots the whole context.

---

## 14. What this project specifically chose, and why

| Choice | Reason |
| :--- | :--- |
| MapStruct for mapping | Compile-time check, no reflection, generated sources are readable |
| Jakarta Bean Validation on domain fields | Declarative, standard, carries constraint metadata for error codes |
| `DomainValidation.validate()` in `build()` | One call merges bean validation with cross-field errors, throws once |
| UUIDs for identity | Database-agnostic, works across SQLite and Postgres |
| IDs generated in the domain (`UUID.randomUUID()` in `build()`) | Seed scripts can build objects and reference them before persisting |
| No JPA relations (except `PayrollSummaryEntity`) | Explicit loading in adapters; no lazy-loading surprises |
| `LocalDateStringConverter` | SQLite date comparison works lexically with ISO strings |
| `NUMERIC(38, 2)` for money | Matches Hibernate's default for `BigDecimal`; simple |
| Two aggregation tables (`PayrollPeriodAggregate`, `PayrollPeriodEarnerRanking`) | Reports read snapshots, not live summaries |
| `PayrollPeriodEarnerPercentile` table | Pre-computed percentile bands per period, read by the ranking report |
| No `PayrollPeriodSnapshot` entity | The "snapshot" is the set of aggregate, ranking, and percentile rows for a period; no extra table |
| Status on aggregate, ranking, and percentile rows | Reports always find what they need with one indexed query |
| JDBC for the earner-ranking query | Windowed ranking is not expressible cleanly with the Criteria API |

---

## 15. When to break the rules

The rules are defaults, not absolutes. The pattern is deliberately bent in a
few places:

1. **`PayrollSummaryEntity` uses `@ManyToOne`.** The payroll list needs to
   sort by `employee.name` and filter by `payrollPeriod.startDate`. A subquery
   per row would be slow at 10,000 employees. The relation is the exception,
   and it is documented.

2. **Denormalized columns on `PayrollSummaryEntity`.** The four assignment
   attributes (`assignmentCountryId`, `assignmentDepartmentId`,
   `assignmentRoleId`, `assignmentEmploymentType`) are copied from the
   assignment to the summary. This allows filtering and grouping on those
   dimensions without a join. The duplication is safe because the assignment's
   placement is frozen once payroll exists.

3. **`PayrollPeriodEarnerRankingJdbcRepository` uses JDBC rather than Spring
   Data JPA.** The ranking query uses a window function; the Criteria API does
   not express it cleanly. The adapter still satisfies the domain port.

All exceptions are visible, documented, and small. The rule is "no JPA
relations" and "no denormalization" — the exceptions serve a specific,
measurable need and do not weaken the overall structure.
