package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import com.salary.management.infrastructure.persistence.repository.EmployeePeriodicAssignmentJpaRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link EmployeePeriodicAssignmentRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/employee-periodic-assignment-adapter.sql")
class EmployeePeriodicAssignmentRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FINANCE =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ENGINEER =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ASSIGNMENT_1 =
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1");
    private static final UUID ASSIGNMENT_2 =
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2");

    @Autowired
    private EmployeePeriodicAssignmentRepositoryAdapter adapter;

    @Autowired
    private EmployeePeriodicAssignmentJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted assignment")
    void saveThenFindByIdReturnsPersistedAssignment() {
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(GRACE)
                .startDate(LocalDate.of(2026, 1, 1))
                .country(country(FR))
                .department(department(ENGINEERING))
                .role(role(ENGINEER))
                .employmentType(EmploymentType.FULL_TIME)
                .inEffect(true)
                .build();

        adapter.save(assignment);
        entityManager.flush();
        entityManager.clear();

        Optional<EmployeePeriodicAssignment> found = adapter.findById(assignment.getId());

        assertTrue(found.isPresent());
        assertEquals(assignment.getId(), found.get().getId());
        assertEquals(GRACE, found.get().getEmployeeId());
        assertEquals(FR, found.get().getCountry().getId());
        assertEquals(ENGINEERING, found.get().getDepartment().getId());
        assertEquals(ENGINEER, found.get().getRole().getId());
        assertEquals(EmploymentType.FULL_TIME, found.get().getEmploymentType());
        assertTrue(found.get().getInEffect());
    }

    @Test
    @DisplayName("save throws when the country does not exist")
    void saveThrowsWhenCountryMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(GRACE)
                .startDate(LocalDate.of(2026, 1, 1))
                .country(country(missing))
                .department(department(ENGINEERING))
                .role(role(ENGINEER))
                .employmentType(EmploymentType.FULL_TIME)
                .inEffect(true)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(assignment));
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        EmployeePeriodicAssignment existing = EmployeePeriodicAssignment.builder()
                .id(ASSIGNMENT_1)
                .employeeId(ADA)
                .startDate(LocalDate.of(2026, 1, 1))
                .country(country(FR))
                .department(department(ENGINEERING))
                .role(role(ENGINEER))
                .employmentType(EmploymentType.FULL_TIME)
                .inEffect(true)
                .build();
        EmployeePeriodicAssignment updated = existing.withInEffect(false);

        adapter.save(updated);
        entityManager.flush();
        entityManager.clear();

        Optional<EmployeePeriodicAssignment> found = adapter.findById(ASSIGNMENT_1);

        assertTrue(found.isPresent());
        assertFalse(found.get().getInEffect());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByEmployeeId returns the employee's assignments")
    void findByEmployeeIdReturnsAssignments() {
        List<EmployeePeriodicAssignment> result = adapter.findByEmployeeId(ADA);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByEmployeeId returns empty when the employee has none")
    void findByEmployeeIdReturnsEmptyWhenNone() {
        List<EmployeePeriodicAssignment> result = adapter.findByEmployeeId(GRACE);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        EmployeePeriodicAssignmentCriteria criteria =
                EmployeePeriodicAssignmentCriteria.builder()
                        .sortBy("startDate")
                        .sortDirection(SortDirection.ASC)
                        .build();

        List<EmployeePeriodicAssignment> result = adapter.list(criteria);

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2026, 1, 1), result.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 7, 1), result.get(1).getStartDate());
    }

    @Test
    @DisplayName("list filters by employee id")
    void listFiltersByEmployeeId() {
        EmployeePeriodicAssignmentCriteria criteria =
                EmployeePeriodicAssignmentCriteria.builder()
                        .employeeId(ADA)
                        .build();

        List<EmployeePeriodicAssignment> result = adapter.list(criteria);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("list filters by country")
    void listFiltersByCountry() {
        EmployeePeriodicAssignmentCriteria criteria =
                EmployeePeriodicAssignmentCriteria.builder()
                        .anyCountryIds(java.util.Set.of(DE))
                        .build();

        List<EmployeePeriodicAssignment> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(ASSIGNMENT_2, result.get(0).getId());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        EmployeePeriodicAssignmentCriteria criteria =
                EmployeePeriodicAssignmentCriteria.builder()
                        .page(0)
                        .size(1)
                        .sortBy("startDate")
                        .sortDirection(SortDirection.ASC)
                        .build();

        DataPage<EmployeePeriodicAssignment> page = adapter.page(criteria);

        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(1, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced assignment")
    void deleteByIdRemovesUnreferencedAssignment() {
        adapter.deleteById(ASSIGNMENT_2);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(ASSIGNMENT_2));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(2, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the assignment is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed a payroll summary that references ASSIGNMENT_1.
        entityManager.createNativeQuery("""
                INSERT INTO payroll_periods
                    (id, name, start_date, end_date, frequency, closed)
                VALUES ('77777777-7777-7777-7777-777777777777',
                        'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO payroll_summaries
                    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
                     assignment_country_id, assignment_department_id, assignment_role_id,
                     assignment_employment_type, base, variable, allowances, employer_taxes,
                     total, currency, status)
                VALUES ('88888888-8888-8888-8888-888888888888',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        '77777777-7777-7777-7777-777777777777',
                        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
                        '11111111-1111-1111-1111-111111111111',
                        '33333333-3333-3333-3333-333333333333',
                        '55555555-5555-5555-5555-555555555555',
                        'FULL_TIME', 1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR', 'DRAFT')
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.deleteById(ASSIGNMENT_1));
        assertTrue(jpaRepository.existsById(ASSIGNMENT_1));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(GRACE)
                .startDate(LocalDate.of(2026, 3, 1))
                .country(country(DE))
                .department(department(FINANCE))
                .role(role(MANAGER))
                .employmentType(EmploymentType.PART_TIME)
                .inEffect(true)
                .build();

        assertNotNull(assignment.getId());
        adapter.save(assignment);

        assertTrue(jpaRepository.existsById(assignment.getId()));
        assertNull(assignment.getEndDate());
    }

    private Country country(UUID id) {
        return Country.builder()
                .id(id)
                .isoCode("XX")
                .name("Placeholder")
                .currencyCode("XXX")
                .build();
    }

    private Department department(UUID id) {
        return Department.builder()
                .id(id)
                .code("PLC")
                .name("Placeholder")
                .build();
    }

    private Role role(UUID id) {
        return Role.builder()
                .id(id)
                .code("PLC")
                .name("Placeholder")
                .build();
    }
}