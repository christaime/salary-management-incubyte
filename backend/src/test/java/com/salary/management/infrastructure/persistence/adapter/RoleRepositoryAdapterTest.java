package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.repository.criteria.RoleCriteria;
import com.salary.management.infrastructure.persistence.repository.RoleJpaRepository;
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

/** Integration tests for {@link RoleRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/role-adapter.sql")
class RoleRepositoryAdapterTest {

    private static final UUID ENGINEER =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MANAGER =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ANALYST =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private RoleRepositoryAdapter adapter;

    @Autowired
    private RoleJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted role")
    void saveThenFindByIdReturnsPersistedRole() {
        Role role = Role.builder()
                .name("Designer")
                .build();

        adapter.save(role);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> found = adapter.findById(role.getId());

        assertTrue(found.isPresent());
        assertEquals(role.getId(), found.get().getId());
        assertEquals("Designer", found.get().getName());
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        Role existing = Role.builder()
                .id(MANAGER)
                .name("Manager")
                .build();
        Role renamed = existing.withName("Senior Manager");

        adapter.save(renamed);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> found = adapter.findById(MANAGER);

        assertTrue(found.isPresent());
        assertEquals("Senior Manager", found.get().getName());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByName returns the matching role")
    void findByNameReturnsMatchingRole() {
        Optional<Role> found = adapter.findByName("Manager");

        assertTrue(found.isPresent());
        assertEquals(MANAGER, found.get().getId());
    }

    @Test
    @DisplayName("findByName returns empty for an unknown name")
    void findByNameReturnsEmptyForUnknownName() {
        assertTrue(adapter.findByName("Architect").isEmpty());
    }

    @Test
    @DisplayName("existsByName reflects the stored rows")
    void existsByNameReflectsStoredRows() {
        assertTrue(adapter.existsByName("Analyst"));
        assertFalse(adapter.existsByName("Architect"));
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        RoleCriteria criteria = RoleCriteria.builder()
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        List<Role> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("Analyst", result.get(0).getName());
        assertEquals("Engineer", result.get(1).getName());
        assertEquals("Manager", result.get(2).getName());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        RoleCriteria criteria = RoleCriteria.builder()
                .keyword("man")
                .build();

        List<Role> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("Manager", result.get(0).getName());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        RoleCriteria criteria = RoleCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Role> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("page returns the last page with the remaining row")
    void pageReturnsLastPage() {
        RoleCriteria criteria = RoleCriteria.builder()
                .page(1)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Role> page = adapter.page(criteria);

        assertEquals(1, page.content().size());
        assertFalse(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced role")
    void deleteByIdRemovesUnreferencedRole() {
        adapter.deleteById(ANALYST);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(ANALYST));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the role is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed an assignment that references ENGINEER through role_id.
        entityManager.createNativeQuery("""
                INSERT INTO countries (id, iso_code, name, currency_code)
                VALUES ('44444444-4444-4444-4444-444444444444', 'FR', 'France', 'EUR')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO departments (id, name)
                VALUES ('55555555-5555-5555-5555-555555555555', 'Engineering')
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
                        '55555555-5555-5555-5555-555555555555',
                        '11111111-1111-1111-1111-111111111111',
                        'FULL_TIME', 1)
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.deleteById(ENGINEER));
        assertTrue(jpaRepository.existsById(ENGINEER));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        Role role = Role.builder()
                .name("Architect")
                .build();

        assertNotNull(role.getId());
        adapter.save(role);

        assertTrue(jpaRepository.existsById(role.getId()));
    }
}