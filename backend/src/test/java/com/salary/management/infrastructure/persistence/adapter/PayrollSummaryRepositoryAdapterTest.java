package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.repository.PayrollSummaryJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link PayrollSummaryRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/payroll-summary-adapter.sql")
class PayrollSummaryRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENGINEERING =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JANUARY =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FEBRUARY =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ASSIGNMENT =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SUMMARY =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private PayrollSummaryRepositoryAdapter adapter;

    @Autowired
    private PayrollSummaryJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted summary")
    void saveThenFindByIdReturnsPersistedSummary() {
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(GRACE)
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.VALIDATED)
                .build();

        adapter.save(summary);
        entityManager.flush();
        entityManager.clear();

        Optional<PayrollSummary> found = adapter.findById(summary.getId());

        assertTrue(found.isPresent());
        assertEquals(summary.getId(), found.get().getId());
        assertEquals(GRACE, found.get().getEmployeeId());
        assertEquals(FEBRUARY, found.get().getPayrollPeriod().getId());
        assertEquals(ASSIGNMENT, found.get().getEmployeePeriodicAssignmentId());
        assertEquals(PayrollSummaryStatus.VALIDATED, found.get().getStatus());
        assertEquals(0, found.get().getAmounts().getTotal().compareTo(new BigDecimal("1350.00")));
    }

    @Test
    @DisplayName("save throws when the employee does not exist")
    void saveThrowsWhenEmployeeMissing() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(missing)
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(summary));
    }

    @Test
    @DisplayName("save throws when the assignment does not exist")
    void saveThrowsWhenAssignmentMissing() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(ADA)
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .employeePeriodicAssignmentId(missing)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(summary));
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByEmployeeIdAndPayrollPeriodId returns the matching summary")
    void findByEmployeeAndPeriodReturnsSummary() {
        Optional<PayrollSummary> found =
                adapter.findByEmployeeIdAndPayrollPeriodId(ADA, JANUARY);

        assertTrue(found.isPresent());
        assertEquals(SUMMARY, found.get().getId());
    }

    @Test
    @DisplayName("findByEmployeeIdAndPayrollPeriodId returns empty when unknown")
    void findByEmployeeAndPeriodReturnsEmpty() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        assertTrue(adapter.findByEmployeeIdAndPayrollPeriodId(ADA, missing).isEmpty());
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .sortBy("status")
                .sortDirection(SortDirection.ASC)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(SUMMARY, result.get(0).getId());
    }

    @Test
    @DisplayName("list filters by employee id")
    void listFiltersByEmployeeId() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .employeeId(ADA)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by employee matricule")
    void listFiltersByEmployeeMatricule() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .employeeMatricule("EMP-0001")
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by date range")
    void listFiltersByDateRange() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                 .rangeStart(LocalDate.of(2026, 1, 1))
                 .rangeEnd(LocalDate.of(2026, 1, 31))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment country")
    void listFiltersByAssignmentCountry() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .anyCountryIds(Set.of(FR))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment department")
    void listFiltersByAssignmentDepartment() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .anyDepartmentIds(Set.of(ENGINEERING))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment role")
    void listFiltersByAssignmentRole() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .anyRoleIds(Set.of(ENGINEER))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .page(0)
                .size(10)
                .sortBy("status")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<PayrollSummary> page = adapter.page(criteria);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(1, page.content().size());
        assertTrue(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes the row")
    void deleteByIdRemovesRow() {
        adapter.deleteById(SUMMARY);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(SUMMARY));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        adapter.deleteById(missing);

        assertEquals(1, jpaRepository.count());
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(GRACE)
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("USD"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertNotNull(summary.getId());
        adapter.save(summary);

        assertTrue(jpaRepository.existsById(summary.getId()));
    }

    private PayrollPeriod payrollPeriod(UUID id, String name,
                                        LocalDate start, LocalDate end) {
        return PayrollPeriod.builder()
                .id(id)
                .name(name)
                .startDate(start)
                .endDate(end)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private Amounts amounts(String currency) {
        return Amounts.builder()
                .base(new BigDecimal("1000.00"))
                .variable(new BigDecimal("100.00"))
                .allowances(new BigDecimal("50.00"))
                .employerTaxes(new BigDecimal("200.00"))
                .currency(currency)
                .build();
    }
}