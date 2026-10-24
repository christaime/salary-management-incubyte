
# Milestone 2 — Persistence Layer

You are generating code for a Spring Boot 3.3 / Java 17 / Gradle / SQLite
project: a take-home assessment for Incubyte. Clean architecture, solo project.

## Working rules (strict)

- File by file. Generate one file, wait for my review, then the next.
- Stop after each numbered group and wait for me to say "next" before continuing.
- Provide a commit message after each group.
- English only.
- 100-char line width.
- Short Javadoc on every class.
- No scope creep. Do not add fields, methods, or classes we did not ask for.
- Do not add audit fields, equals/hashCode, setters, or JPA relations.

## Project layout

Package root: `com.salary.management`

| Concern | Package |
|---|---|
| Pagination | `com.salary.management.common.pagination` |
| Validation | `com.salary.management.common.validation` |
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

## Existing domain (Milestone 1 — do not recreate)

**Validation** in `common/validation/`: `ValidationError`, `DomainValidationException`, `DomainValidation`.

**Enums** in `domain/model/enums/`: `EmploymentType`, `PayrollPeriodFrequency`, `PayrollSummaryStatus`, `EarnerRankingDirection`, `GroupKey`, `ReportKind`.

**Value object** in `domain/model/values/`: `Amounts` — base, variable, allowances, employerTaxes, total, currency.

**Domain models** in `domain/model/`: `Country`, `Department`, `Role`, `Employee`, `CurrencyRate`, `EmployeePeriodicAssignment`, `PayrollPeriod`, `PayrollSummary`, `PayrollPeriodSnapshot`, `PayrollPeriodAggregate`, `PayrollPeriodEarnerRanking`.

Foreign reference rule (important):
- References to `Country`, `Department`, `Role`, `PayrollPeriod` are **domain objects** in the domain classes.
- References to `Employee`, `EmployeePeriodicAssignment`, `PayrollSummary` are **UUIDs**.
- `Employee.originCountry` is nullable.

**Repository ports** in `domain/repository/`: one per entity (11 total).

## Design decisions

### Repository port shape

Every repository has:
- `save(...)`, `findById(UUID)`, `deleteById(UUID)` — commands and lookups
- `list(Criteria)` → `List<T>`
- `page(Criteria)` → `DataPage<T>`

No `search(...)`. No `findAll()`. No listing without criteria.

Known-key lookups like `findByIsoCode(String)`, `existsByName(String)`,
`findByMatricule(String)` remain as-is.

### Criteria contract

Every `<Entity>Criteria` implements `com.salary.management.common.pagination.Criteria`:

```java
public interface Criteria {
    String sortBy();                      // requested field, or null
    SortDirection sortDirection();        // requested direction, or null
    Set<String> sortableFields();         // allow-list
    String defaultSortField();            // used when missing or invalid
    SortDirection defaultSortDirection(); // direction for the default field
    int page();
    int size();
}
```

Criteria live in `domain/repository/criteria/`. Each has a builder.

### JPA entities

- Entities hold **UUID columns only** for foreign references — no `@ManyToOne`, no object references, no lazy loading.
- `@Id UUID id` with `@GeneratedValue(strategy = GenerationType.UUID)`.
- `@Enumerated(EnumType.STRING)` for enums.
- `Amounts` maps to an `@Embeddable AmountsEmbeddable` with its six fields.
- No audit columns.

### Mappers

MapStruct. Signature pattern:

```java
PayrollSummary toDomain(PayrollSummaryEntity entity, PayrollPeriod period);
PayrollSummaryEntity toEntity(PayrollSummary summary);
```

`toDomain` receives loaded domain objects for foreign references.
`toEntity` uses the UUIDs already present in the domain object.

### Adapters

Adapters implement the domain ports. For reads needing foreign references, the
adapter loads the referenced entities and passes them to the mapper.

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

For `page(criteria)`:
```java
Specification<PayrollSummaryEntity> spec = PayrollSummarySpecification.build(criteria);
Pageable pageable = CriteriaUtils.toPageable(criteria);
Page<PayrollSummaryEntity> page = jpaRepository.findAll(spec, pageable);
return toDataPage(page);
```

For `list(criteria)`:
```java
Specification<PayrollSummaryEntity> spec = PayrollSummarySpecification.build(criteria);
Sort sort = CriteriaUtils.toSpringSort(criteria);
List<PayrollSummaryEntity> entities = jpaRepository.findAll(spec, sort);
return entities.stream().map(this::toDomain).toList();
```

### CriteriaUtils

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

---

# Groups to generate

Generate each group in order. Stop after each group and wait for "next".

---

## GROUP 2.1 — Pagination types

Generate three files in `com.salary.management.common.pagination`:

1. **`SortDirection.java`** — a simple enum with `ASC` and `DESC`.

2. **`Criteria.java`** — an interface with exactly these seven methods:
    - `String sortBy()`
    - `SortDirection sortDirection()`
    - `Set<String> sortableFields()`
    - `String defaultSortField()`
    - `SortDirection defaultSortDirection()`
    - `int page()`
    - `int size()`

3. **`DataPage.java`** — a record:
    - Fields: `List<T> content`, `int page`, `int size`, `long totalElements`, `int totalPages`, `boolean first`, `boolean last`, `boolean empty`
    - Compact constructor that computes `totalPages`, `first`, `last`, `empty` from the other fields
    - Static `empty()`
    - `map(Function<T,R>)` to convert content
    - `numberOfElements()` and `hasContent()`

Generate the three files, one after the other. Wait for review after all three.
Provide a commit message for the group.

**Commit message:**
`Milestone 2: add pagination types`

**Stop here. Wait for "next".**

---

## GROUP 2.2 — CriteriaUtils

Generate one file in `com.salary.management.infrastructure.persistence.adapter.utils`:

**`CriteriaUtils.java`** — as shown in the design section above.

**Commit message:**
`Milestone 2: add criteria utils`

**Stop here. Wait for "next".**

---

## GROUP 2.3 — JPA entities and embeddable

Generate twelve files:

**In `com.salary.management.infrastructure.persistence.embeddable`:**

1. **`AmountsEmbeddable.java`** — with fields `base`, `variable`, `allowances`, `employerTaxes`, `total` (all `BigDecimal`), `currency` (String).

**In `com.salary.management.infrastructure.persistence.entity`:**

2. `CountryEntity` — id, isoCode, name, currencyCode
3. `DepartmentEntity` — id, name
4. `RoleEntity` — id, name
5. `EmployeeEntity` — id, matricule, name, email, birthDate, originCountryId (UUID, nullable)
6. `CurrencyRateEntity` — id, currencyCode, referenceCurrencyCode, rate
7. `EmployeePeriodicAssignmentEntity` — id, employeeId (UUID), startDate, endDate (nullable), countryId (UUID), departmentId (UUID), roleId (UUID), employmentType (EnumType.STRING), inEffect
8. `PayrollPeriodEntity` — id, name, startDate, endDate, frequency (EnumType.STRING), closed
9. `PayrollSummaryEntity` — id, employeeId (UUID), payrollPeriodId (UUID), employeePeriodicAssignmentId (UUID), `@Embedded AmountsEmbeddable amounts`, status (EnumType.STRING)
10. `PayrollPeriodSnapshotEntity` — id, payrollPeriodId (UUID), status (EnumType.STRING), warnings (`@ElementCollection List<String>` with a `@CollectionTable`, or a simple `@Column` storing a comma-separated string — pick one and justify briefly)
11. `PayrollPeriodAggregateEntity` — id, payrollPeriodId (UUID), countryId (UUID), departmentId (UUID), roleId (UUID), `@Embedded originalAmounts`, `@Embedded referenceAmounts`, employeeCount
12. `PayrollPeriodEarnerRankingEntity` — id, payrollPeriodId (UUID), countryId (UUID), departmentId (UUID), roleId (UUID), direction (EnumType.STRING), rank, employeeId (UUID), amount (BigDecimal)

For each entity:
- `@Entity`, `@Table(name = "...")` with a plural snake_case name
- `@Id UUID id` with `@GeneratedValue(strategy = GenerationType.UUID)`
- No `@ManyToOne`, no relations, UUID columns only
- Use Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- No audit columns

Add unique constraints where the domain enforces them:
- `CountryEntity.isoCode` unique
- `DepartmentEntity.name` unique
- `RoleEntity.name` unique
- `EmployeeEntity.matricule` unique
- `CurrencyRateEntity` — unique on `(currencyCode, referenceCurrencyCode)`
- `PayrollSummaryEntity` — unique on `(employeeId, payrollPeriodId)`
- `PayrollPeriodAggregateEntity` — unique on `(payrollPeriodId, countryId, departmentId, roleId)`
- `PayrollPeriodEarnerRankingEntity` — unique on `(payrollPeriodId, countryId, departmentId, roleId, direction, rank)`
- `PayrollPeriodEntity.name` unique

For `AmountsEmbeddable`, use `@Embeddable`. When used twice in `PayrollPeriodAggregateEntity`, override the column names:

```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "base", column = @Column(name = "original_base")),
    @AttributeOverride(name = "variable", column = @Column(name = "original_variable")),
    ...
})
private AmountsEmbeddable originalAmounts;

@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "base", column = @Column(name = "reference_base")),
    ...
})
private AmountsEmbeddable referenceAmounts;
```

Generate the twelve files, one after the other. Wait for review after all twelve.
Provide a commit message for the group.

**Commit message:**
`Milestone 2: add JPA entities`

**Stop here. Wait for "next".**

---

## GROUP 2.4 — Flyway migration

Generate one file:

`backend/src/main/resources/db/migration/V1__initial_schema.sql`

The schema must match the JPA entities exactly. Tables use snake_case plural
names. Columns use snake_case. Foreign key columns are UUIDs. Add indexes on
foreign key columns and on columns used for filtering or sorting.

Include:
- All 11 tables
- Unique constraints as listed in group 2.3
- Foreign keys where the domain enforces them (optional — document if you skip any)
- Indexes on: `employee.matricule`, `payroll_summary.employee_id`, `payroll_summary.payroll_period_id`, `employee_periodic_assignment.employee_id`, `payroll_period.name`, `payroll_period_aggregate.payroll_period_id`, `payroll_period_earner_ranking.payroll_period_id`, `payroll_period_snapshot.payroll_period_id`

SQLite notes:
- Use `TEXT` for UUIDs
- Use `TEXT` for dates (ISO format) and for enum values
- Use `REAL` or `TEXT` for `BigDecimal` — **document which you choose and why**
- SQLite does not enforce foreign keys by default; the migration can declare them for documentation, and enforcement is enabled by a `PRAGMA` at connection time (to be added in a later milestone)

**Commit message:**
`Milestone 2: add initial schema migration`

**Stop here. Wait for "next".**

---

## GROUP 2.5 — MapStruct persistence mappers

Generate eleven mappers in `com.salary.management.infrastructure.persistence.mapper`:

1. `CountryPersistenceMapper` — `toEntity(Country)`, `toDomain(CountryEntity)`, `toDomainList(List<CountryEntity>)`
2. `DepartmentPersistenceMapper` — same shape
3. `RolePersistenceMapper` — same shape
4. `EmployeePersistenceMapper` — `toEntity(Employee)` maps `originCountry` to `originCountryId`. `toDomain(EmployeeEntity, Country originCountry)` accepts the resolved Country (nullable).
5. `CurrencyRatePersistenceMapper` — simple
6. `EmployeePeriodicAssignmentPersistenceMapper` — `toEntity(EmployeePeriodicAssignment)` maps `country`, `department`, `role` to their UUIDs. `toDomain(entity, Country, Department, Role)`.
7. `PayrollPeriodPersistenceMapper` — simple
8. `PayrollSummaryPersistenceMapper` — `toEntity(PayrollSummary)` maps `payrollPeriod` to `payrollPeriodId`. `toDomain(entity, PayrollPeriod)`.
9. `PayrollPeriodSnapshotPersistenceMapper` — `toDomain(entity, PayrollPeriod)`
10. `PayrollPeriodAggregatePersistenceMapper` — `toDomain(entity, PayrollPeriod, Country, Department, Role)`
11. `PayrollPeriodEarnerRankingPersistenceMapper` — `toDomain(entity, PayrollPeriod, Country, Department, Role)`

All mappers: `@Mapper(componentModel = "spring")`.

Use `@Mapping` annotations where field names differ.

**Commit message:**
`Milestone 2: add persistence mappers`

**Stop here. Wait for "next".**

---

## GROUP 2.6 — Spring Data JPA repositories

Generate eleven interfaces in `com.salary.management.infrastructure.persistence.repository`:

1. `CountryJpaRepository extends JpaRepository<CountryEntity, UUID>, JpaSpecificationExecutor<CountryEntity>`
    - `Optional<CountryEntity> findByIsoCode(String)`
    - `boolean existsByIsoCode(String)`
2. `DepartmentJpaRepository` — same shape, `findByName`, `existsByName`
3. `RoleJpaRepository` — same shape
4. `EmployeeJpaRepository` — `findByMatricule`, `existsByMatricule`
5. `CurrencyRateJpaRepository` — `findByCurrencyCodeAndReferenceCurrencyCode`
6. `EmployeePeriodicAssignmentJpaRepository` — `findByEmployeeId`
7. `PayrollPeriodJpaRepository` — `findByName`
8. `PayrollSummaryJpaRepository` — `findByEmployeeIdAndPayrollPeriodId`
9. `PayrollPeriodSnapshotJpaRepository` — `findByPayrollPeriodId`, `deleteByPayrollPeriodId`
10. `PayrollPeriodAggregateJpaRepository` — `findByPayrollPeriodId`, `deleteByPayrollPeriodId`
11. `PayrollPeriodEarnerRankingJpaRepository` — `findByPayrollPeriodId`, `deleteByPayrollPeriodId`

All annotated `@Repository`.

**Commit message:**
`Milestone 2: add JPA repositories`

**Stop here. Wait for "next".**

---

## GROUP 2.7 — Criteria classes and repository adapters

This group has two parts.

### Part A — Criteria classes (11)

Generate one criteria class per entity in `com.salary.management.domain.repository.criteria`:

`CountryCriteria`, `DepartmentCriteria`, `RoleCriteria`, `EmployeeCriteria`,
`CurrencyRateCriteria`, `EmployeePeriodicAssignmentCriteria`,
`PayrollPeriodCriteria`, `PayrollSummaryCriteria`,
`PayrollPeriodSnapshotCriteria`, `PayrollPeriodAggregateCriteria`,
`PayrollPeriodEarnerRankingCriteria`.

Each:
- Implements `Criteria`
- Has a private constructor taking a `Builder`
- Has a static `builder()`
- Has filter fields (start with minimal or none — just the fields the repository actually needs today)
- Has sort fields: `sortBy`, `sortDirection`
- Has `page`, `size`
- Declares `SORTABLE_FIELDS` as a `Set<String>`
- Implements all seven `Criteria` methods
- Has a static inner `Builder`

For filter fields: only include what the repository ports need. If a repository has no filters today, the criteria is empty (only sort + pagination).

### Part B — Repository adapters (11)

Generate one adapter per repository in `com.salary.management.infrastructure.persistence.adapter`.

Each adapter:
- `@Component`, `@RequiredArgsConstructor`
- Implements the domain repository port
- Injects the corresponding `JpaRepository` and `PersistenceMapper`
- Injects the JPA repositories it needs to resolve foreign references
- Implements `save`, `findById`, `deleteById`, `list`, `page`, and any known-key lookups
- Uses `CriteriaUtils.toSpringSort(...)` in `list` and `CriteriaUtils.toPageable(...)` in `page`
- Uses a private `toDomain(entity)` helper that loads foreign references and calls the mapper
- Uses a private `toDataPage(Page<Entity>)` helper that maps content and constructs `DataPage`

For the aggregate and ranking adapters, `findByPayrollPeriodId` returns a `List`, and `saveAll(List)` is used instead of `save`.

**Commit message:**
`Milestone 2: add criteria classes and repository adapters`

**Stop here. Wait for "next".**

---

## GROUP 2.8 — Specifications

Generate eleven specification classes in
`com.salary.management.infrastructure.persistence.specification`:

`CountrySpecification`, `DepartmentSpecification`, `RoleSpecification`,
`EmployeeSpecification`, `CurrencyRateSpecification`,
`EmployeePeriodicAssignmentSpecification`, `PayrollPeriodSpecification`,
`PayrollSummarySpecification`, `PayrollPeriodSnapshotSpecification`,
`PayrollPeriodAggregateSpecification`, `PayrollPeriodEarnerRankingSpecification`.

Each:
- `@Component`
- Static `build(XCriteria criteria)` returning `Specification<XEntity>`
- Builds predicates from the criteria's filters
- Returns `criteriaBuilder.conjunction()` when the criteria has no filters
- Handles null filters gracefully

For `PayrollSummarySpecification`, include filters on:
- `employeeId`
- `payrollPeriodId` (or a date range that resolves to periods)
- `status`

For specifications whose criteria currently have no filters, return `conjunction()`.

**Commit message:**
`Milestone 2: add specifications`

**Stop here. Wait for "next".**

---

## GROUP 2.9 — Adapter integration tests

Generate eleven integration tests in
`src/test/java/com/salary/management/infrastructure/persistence/adapter/`:

One test class per adapter. Each uses `@SpringBootTest` (or a shared base test
class if one exists — check first, don't invent one). Uses `@Transactional` so
each test rolls back.

For each test class, cover:
- `save` then `findById` returns the same object
- `save` on an existing id updates
- `deleteById` removes the row
- `findById` returns empty for a missing id
- Each known-key lookup (`findByIsoCode`, etc.)
- `list(criteria)` returns matching rows, sorted
- `page(criteria)` returns a page with correct `totalElements` and `totalPages`

Use JUnit 5 with `@DisplayName` on every test. Build domain objects via their
builders in the test setup.

If the project already has a base test class (check `src/test/java`), use it.
If not, do not invent one — write each test class standalone with
`@SpringBootTest` and `@Transactional`.

**Commit message:**
`Milestone 2: add adapter integration tests`

**Stop here. Milestone 2 is complete.**

---

# Rules recap

- One group at a time. Stop after each.
- Wait for "next" before proceeding.
- No additions beyond what each group lists.
- If a design decision is ambiguous, ask before generating.
- Every file needs a short Javadoc.
- Follow the 100-char line width.

