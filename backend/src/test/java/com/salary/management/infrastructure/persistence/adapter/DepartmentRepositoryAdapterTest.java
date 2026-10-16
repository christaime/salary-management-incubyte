package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.repository.criteria.DepartmentCriteria;
import com.salary.management.infrastructure.persistence.repository.DepartmentJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link DepartmentRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/department-adapter.sql")
class DepartmentRepositoryAdapterTest {

    private static final UUID ENGINEERING =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FINANCE =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HR =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private DepartmentRepositoryAdapter adapter;

    @Autowired
    private DepartmentJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted department")
    void saveThenFindByIdReturnsPersistedDepartment() {
        Department department = Department.builder()
                .name("Marketing")
                .build();

        adapter.save(department);
        entityManager.flush();
        entityManager.clear();

        Optional<Department> found = adapter.findById(department.getId());

        assertTrue(found.isPresent());
        assertEquals(department.getId(), found.get().getId());
        assertEquals("Marketing", found.get().getName());
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        Department existing = Department.builder()
                .id(FINANCE)
                .name("Finance")
                .build();
        Department renamed = existing.withName("Financial Services");

        adapter.save(renamed);
        entityManager.flush();
        entityManager.clear();

        Optional<Department> found = adapter.findById(FINANCE);

        assertTrue(found.isPresent());
        assertEquals("Financial Services", found.get().getName());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByName returns the matching department")
    void findByNameReturnsMatchingDepartment() {
        Optional<Department> found = adapter.findByName("Finance");

        assertTrue(found.isPresent());
        assertEquals(FINANCE, found.get().getId());
    }

    @Test
    @DisplayName("findByName returns empty for an unknown name")
    void findByNameReturnsEmptyForUnknownName() {
        assertTrue(adapter.findByName("Legal").isEmpty());
    }

    @Test
    @DisplayName("existsByName reflects the stored rows")
    void existsByNameReflectsStoredRows() {
        assertTrue(adapter.existsByName("Human Resources"));
        assertFalse(adapter.existsByName("Legal"));
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        DepartmentCriteria criteria = DepartmentCriteria.builder()
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        List<Department> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("Engineering", result.get(0).getName());
        assertEquals("Finance", result.get(1).getName());
        assertEquals("Human Resources", result.get(2).getName());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        DepartmentCriteria criteria = DepartmentCriteria.builder()
                .keyword("fin")
                .build();

        List<Department> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("Finance", result.get(0).getName());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        DepartmentCriteria criteria = DepartmentCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Department> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("page returns the last page with the remaining row")
    void pageReturnsLastPage() {
        DepartmentCriteria criteria = DepartmentCriteria.builder()
                .page(1)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Department> page = adapter.page(criteria);

        assertEquals(1, page.content().size());
        assertFalse(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced department")
    void deleteByIdRemovesUnreferencedDepartment() {
        adapter.deleteById(HR);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(HR));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the department is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed an assignment that references ENGINEERING through department_id.
        entityManager.createNativeQuery("""
                INSERT INTO countries (id, iso_code, name, currency_code)
                VALUES ('44444444-4444-4444-4444-444444444444', 'FR', 'France', 'EUR')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO roles (id, name)
                VALUES ('55555555-5555-5555-5555-555555555555', 'Engineer')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        'EMP-0001', 'Ada Lovelace', 'ada@example.com', NULL, NULL)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO employee_periodic_assignments
                    (id, employee_id, start_date, end_date, country_id,
                     department_id, role_id, employment_type, in_effect)
                VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        '2026-01-01', NULL,
                        '44444444-4444-4444-4444-444444444444',
                        '11111111-1111-1111-1111-111111111111',
                        '55555555-5555-5555-5555-555555555555',
                        'FULL_TIME', 1)
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.deleteById(ENGINEERING));
        assertTrue(jpaRepository.existsById(ENGINEERING));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        Department department = Department.builder()
                .name("Legal")
                .build();

        assertNotNull(department.getId());
        adapter.save(department);

        assertTrue(jpaRepository.existsById(department.getId()));
    }
}