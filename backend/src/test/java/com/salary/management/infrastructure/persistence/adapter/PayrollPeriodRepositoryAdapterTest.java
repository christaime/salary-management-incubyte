package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodJpaRepository;
import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link PayrollPeriodRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/payroll-period-adapter.sql")
class PayrollPeriodRepositoryAdapterTest {

    private static final UUID JANUARY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FEBRUARY =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MARCH_FIRST_HALF =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private PayrollPeriodRepositoryAdapter adapter;

    @Autowired
    private PayrollPeriodJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted period")
    void saveThenFindByIdReturnsPersistedPeriod() {
        PayrollPeriod period = PayrollPeriod.builder()
                .name("April 2026")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 4, 30))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();

        adapter.save(period);
        entityManager.flush();
        entityManager.clear();

        Optional<PayrollPeriod> found = adapter.findById(period.getId());

        assertTrue(found.isPresent());
        assertEquals(period.getId(), found.get().getId());
        assertEquals("April 2026", found.get().getName());
        assertEquals(LocalDate.of(2026, 4, 1), found.get().getStartDate());
        assertEquals(LocalDate.of(2026, 4, 30), found.get().getEndDate());
        assertEquals(PayrollPeriodFrequency.MONTHLY, found.get().getFrequency());
        assertFalse(found.get().getClosed());
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        PayrollPeriod existing = PayrollPeriod.builder()
                .id(FEBRUARY)
                .name("February 2026")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
        PayrollPeriod updated = existing.withClosed(true);

        adapter.save(updated);
        entityManager.flush();
        entityManager.clear();

        Optional<PayrollPeriod> found = adapter.findById(FEBRUARY);

        assertTrue(found.isPresent());
        assertTrue(found.get().getClosed());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByName returns the matching period")
    void findByNameReturnsMatchingPeriod() {
        Optional<PayrollPeriod> found = adapter.findByName("First Half of March 2026");

        assertTrue(found.isPresent());
        assertEquals(MARCH_FIRST_HALF, found.get().getId());
    }

    @Test
    @DisplayName("findByName returns empty for an unknown name")
    void findByNameReturnsEmptyForUnknownName() {
        assertTrue(adapter.findByName("December 2099").isEmpty());
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .sortBy("startDate")
                .sortDirection(SortDirection.ASC)
                .build();

        List<PayrollPeriod> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("January 2026", result.get(0).getName());
        assertEquals("February 2026", result.get(1).getName());
        assertEquals("First Half of March 2026", result.get(2).getName());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .keyword("feb")
                .build();

        List<PayrollPeriod> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("February 2026", result.get(0).getName());
    }

    @Test
    @DisplayName("list filters by frequency")
    void listFiltersByFrequency() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .frequency(PayrollPeriodFrequency.BI_WEEKLY)
                .build();

        List<PayrollPeriod> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("First Half of March 2026", result.get(0).getName());
    }

    @Test
    @DisplayName("list filters by closed flag")
    void listFiltersByClosedFlag() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .isClosed(true)
                .build();

        List<PayrollPeriod> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("First Half of March 2026", result.get(0).getName());
    }

    @Test
    @DisplayName("list filters by date range")
    void listFiltersByDateRange() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 3, 1))
                .build();

        List<PayrollPeriod> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("February 2026", result.get(0).getName());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("startDate")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<PayrollPeriod> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("page returns the last page with the remaining row")
    void pageReturnsLastPage() {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .page(1)
                .size(2)
                .sortBy("startDate")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<PayrollPeriod> page = adapter.page(criteria);

        assertEquals(1, page.content().size());
        assertFalse(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced period")
    void deleteByIdRemovesUnreferencedPeriod() {
        adapter.deleteById(FEBRUARY);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(FEBRUARY));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the period is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed an aggregate that references JANUARY.
        entityManager.createNativeQuery("""
                INSERT INTO countries (id, iso_code, name, currency_code)
                VALUES ('44444444-4444-4444-4444-444444444444', 'FR', 'France', 'EUR')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO departments (id, name)
                VALUES ('55555555-5555-5555-5555-555555555555', 'Engineering')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO roles (id, name)
                VALUES ('66666666-6666-6666-6666-666666666666', 'Engineer')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO payroll_period_aggregates
                    (id, payroll_period_id, status, country_id, department_id, role_id,
                     employment_type,
                     original_base, original_variable, original_allowances,
                     original_employer_taxes, original_total, original_currency,
                     reference_base, reference_variable, reference_allowances,
                     reference_employer_taxes, reference_total, reference_currency,
                     employee_count)
                VALUES ('77777777-7777-7777-7777-777777777777',
                        '11111111-1111-1111-1111-111111111111',
                        'DRAFT',
                        '44444444-4444-4444-4444-444444444444',
                        '55555555-5555-5555-5555-555555555555',
                        '66666666-6666-6666-6666-666666666666',
                        'FULL_TIME',
                        1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR',
                        1080.00, 108.00, 54.00, 216.00, 1458.00, 'EUR',
                        1)
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.deleteById(JANUARY));
        assertTrue(jpaRepository.existsById(JANUARY));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        PayrollPeriod period = PayrollPeriod.builder()
                .name("May 2026")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();

        assertNotNull(period.getId());
        adapter.save(period);

        assertTrue(jpaRepository.existsById(period.getId()));
    }
}