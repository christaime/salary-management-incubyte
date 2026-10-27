package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.infrastructure.persistence.repository.EmployeeJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link EmployeeRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/employee-adapter.sql")
class EmployeeRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ALAN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FINANCE =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ENGINEER =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    @Autowired
    private EmployeeRepositoryAdapter adapter;

    @Autowired
    private EmployeeJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted employee")
    void saveThenFindByIdReturnsPersistedEmployee() {
        Employee employee = Employee.builder()
                .matricule("EMP-0010")
                .name("Katherine Johnson")
                .email("katherine@example.com")
                .birthDate(LocalDate.of(1918, 8, 26))
                .originCountry(country(FR))
                .build();

        adapter.save(employee);
        entityManager.flush();
        entityManager.clear();

        Optional<Employee> found = adapter.findById(employee.getId());

        assertTrue(found.isPresent());
        assertEquals(employee.getId(), found.get().getId());
        assertEquals("EMP-0010", found.get().getMatricule());
        assertEquals("Katherine Johnson", found.get().getName());
        assertEquals("katherine@example.com", found.get().getEmail());
        assertEquals(LocalDate.of(1918, 8, 26), found.get().getBirthDate());
        assertNotNull(found.get().getOriginCountry());
        assertEquals(FR, found.get().getOriginCountry().getId());
    }

    @Test
    @DisplayName("save persists an employee with null origin country")
    void savePersistsEmployeeWithNullOriginCountry() {
        Employee employee = Employee.builder()
                .matricule("EMP-0011")
                .name("No Country")
                .email("none@example.com")
                .build();

        adapter.save(employee);
        entityManager.flush();
        entityManager.clear();

        Optional<Employee> found = adapter.findById(employee.getId());

        assertTrue(found.isPresent());
        assertNull(found.get().getOriginCountry());
        assertNull(found.get().getBirthDate());
    }

    @Test
    @DisplayName("save throws when the origin country does not exist")
    void saveThrowsWhenOriginCountryMissing() {
        UUID missingCountry = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Employee employee = Employee.builder()
                .matricule("EMP-0012")
                .name("Bad Country")
                .email("bad@example.com")
                .originCountry(country(missingCountry))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(employee));
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        Employee existing = Employee.builder()
                .id(ADA)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
        Employee renamed = existing.withName("Ada King");

        adapter.save(renamed);
        entityManager.flush();
        entityManager.clear();

        Optional<Employee> found = adapter.findById(ADA);

        assertTrue(found.isPresent());
        assertEquals("Ada King", found.get().getName());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByMatricule returns the matching employee")
    void findByMatriculeReturnsMatchingEmployee() {
        Optional<Employee> found = adapter.findByMatricule("EMP-0002");

        assertTrue(found.isPresent());
        assertEquals(GRACE, found.get().getId());
        assertNotNull(found.get().getOriginCountry());
        assertEquals(DE, found.get().getOriginCountry().getId());
    }

    @Test
    @DisplayName("findByMatricule returns empty for an unknown matricule")
    void findByMatriculeReturnsEmptyForUnknownMatricule() {
        assertTrue(adapter.findByMatricule("EMP-9999").isEmpty());
    }

    @Test
    @DisplayName("existsByMatricule reflects the stored rows")
    void existsByMatriculeReflectsStoredRows() {
        assertTrue(adapter.existsByMatricule("EMP-0003"));
        assertFalse(adapter.existsByMatricule("EMP-9999"));
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .sortBy("matricule")
                .sortDirection(SortDirection.ASC)
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("EMP-0001", result.get(0).getMatricule());
        assertEquals("EMP-0002", result.get(1).getMatricule());
        assertEquals("EMP-0003", result.get(2).getMatricule());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .keyword("grace")
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("EMP-0002", result.get(0).getMatricule());
    }

    @Test
    @DisplayName("list filters by matricule")
    void listFiltersByMatricule() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .matricule("EMP-0003")
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(ALAN, result.get(0).getId());
        assertNull(result.get(0).getOriginCountry());
    }

    @Test
    @DisplayName("list filters by origin country")
    void listFiltersByOriginCountry() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyOriginCountryIds(Set.of(FR))
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(ADA, result.get(0).getId());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("matricule")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Employee> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced employee")
    void deleteByIdRemovesUnreferencedEmployee() {
        adapter.deleteById(ALAN);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(ALAN));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the employee is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed an assignment that references ADA through employee_id.
        entityManager.createNativeQuery("""
                INSERT INTO employee_periodic_assignments
                    (id, employee_id, start_date, end_date, country_id,
                     department_id, role_id, employment_type, frequency, in_effect)
                VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        '2026-01-01', NULL,
                        '11111111-1111-1111-1111-111111111111',
                        '55555555-5555-5555-5555-555555555555',
                        '66666666-6666-6666-6666-666666666666',
                        'FULL_TIME', 'MONTHLY',1)
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> adapter.deleteById(ADA));
        assertTrue(jpaRepository.existsById(ADA));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        Employee employee = Employee.builder()
                .matricule("EMP-0020")
                .name("Margaret Hamilton")
                .email("margaret@example.com")
                .build();

        assertNotNull(employee.getId());
        adapter.save(employee);

        assertTrue(jpaRepository.existsById(employee.getId()));
    }

    // ------------------------------------------------------------------
    // Assignment filters
    // ------------------------------------------------------------------

    @Test
    @DisplayName("list filters by assignment country")
    void listFiltersByAssignmentCountry() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyAssignmentCountryIds(Set.of(FR))
                .build();

        List<Employee> result = adapter.list(criteria);

        // ADA has the FR assignment; GRACE is DE; ALAN has none.
        assertEquals(1, result.size());
        assertEquals(ADA, result.get(0).getId());
    }

    @Test
    @DisplayName("list filters by assignment country returns empty when no assignment matches")
    void listFiltersByAssignmentCountryReturnsEmptyWhenNoMatch() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyAssignmentCountryIds(Set.of(UUID.fromString(
                        "99999999-9999-9999-9999-999999999999")))
                .build();

        List<Employee> result = adapter.list(criteria);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("list filters by assignment department")
    void listFiltersByAssignmentDepartment() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyDepartmentIds(Set.of(FINANCE))
                .build();

        List<Employee> result = adapter.list(criteria);

        // GRACE is the only employee with a FINANCE assignment.
        assertEquals(1, result.size());
        assertEquals(GRACE, result.get(0).getId());
    }

    @Test
    @DisplayName("list filters by assignment role")
    void listFiltersByAssignmentRole() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyRoleIds(Set.of(ENGINEER))
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(ADA, result.get(0).getId());
    }

    @Test
    @DisplayName("list filters by employment type")
    void listFiltersByEmploymentType() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyEmploymentTypes(Set.of(EmploymentType.PART_TIME))
                .build();

        List<Employee> result = adapter.list(criteria);

        // GRACE has PART_TIME; ADA has FULL_TIME.
        assertEquals(1, result.size());
        assertEquals(GRACE, result.get(0).getId());
    }

    @Test
    @DisplayName("list returns employees for multiple values of the same filter")
    void listFiltersByMultipleAssignmentCountries() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyAssignmentCountryIds(Set.of(FR, DE))
                .build();

        List<Employee> result = adapter.list(criteria);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getId().equals(ADA)));
        assertTrue(result.stream().anyMatch(e -> e.getId().equals(GRACE)));
    }

    @Test
    @DisplayName("list combines assignment filters (AND semantics across fields)")
    void listCombinesAssignmentFilters() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyAssignmentCountryIds(Set.of(FR))
                .anyDepartmentIds(Set.of(ENGINEERING))
                .anyRoleIds(Set.of(ENGINEER))
                .anyEmploymentTypes(Set.of(EmploymentType.FULL_TIME))
                .build();

        List<Employee> result = adapter.list(criteria);

        // Only ADA matches all four.
        assertEquals(1, result.size());
        assertEquals(ADA, result.get(0).getId());
    }

    @Test
    @DisplayName("list excludes employees without an assignment when an assignment filter is set")
    void listExcludesEmployeesWithoutAssignment() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .anyAssignmentCountryIds(Set.of(FR, DE))
                .build();

        List<Employee> result = adapter.list(criteria);

        // ALAN has no assignment, so even though the filter has values, ALAN
        // is not returned. The join with employee_periodic_assignments is
        // effectively an INNER JOIN.
        assertTrue(result.stream().noneMatch(e -> e.getId().equals(ALAN)));
    }

    @Test
    @DisplayName("list without assignment filters returns all employees, with or without assignment")
    void listWithoutAssignmentFiltersReturnsAll() {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .build();

        List<Employee> result = adapter.list(criteria);

        // All 3 employees, including ALAN who has no assignment.
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getId().equals(ALAN)));
    }
    private Country country(UUID id) {
        return Country.builder()
                .id(id)
                .isoCode("XX")
                .name("Placeholder")
                .currencyCode("XXX")
                .build();
    }
}