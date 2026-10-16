package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.criteria.PayrollPeriodAggregateCriteria;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodAggregateJpaRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link PayrollPeriodAggregateRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/payroll-period-aggregate-adapter.sql")
class PayrollPeriodAggregateRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ENGINEER =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID JANUARY =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FEBRUARY =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID AGG_FR =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID AGG_DE =
            UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Autowired
    private PayrollPeriodAggregateRepositoryAdapter adapter;

    @Autowired
    private PayrollPeriodAggregateJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("saveAll then findByPayrollPeriodId returns the saved aggregates")
    void saveAllThenFindByPayrollPeriodIdReturnsSaved() {
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(2)
                .build();

        adapter.saveAll(List.of(aggregate));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodAggregate> result = adapter.findByPayrollPeriodId(FEBRUARY);

        assertEquals(1, result.size());
        assertEquals(aggregate.getId(), result.get(0).getId());
        assertEquals(FR, result.get(0).getCountry().getId());
        assertEquals(EmploymentType.FULL_TIME, result.get(0).getEmploymentType());
        assertEquals(2, result.get(0).getEmployeeCount());
        assertEquals(0, result.get(0).getOriginalAmounts().getTotal()
                .compareTo(new BigDecimal("1350.00")));
    }

    @Test
    @DisplayName("saveAll throws when the payroll period does not exist")
    void saveAllThrowsWhenPayrollPeriodMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(missing, "Missing",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(aggregate)));
    }

    @Test
    @DisplayName("saveAll throws when the country does not exist")
    void saveAllThrowsWhenCountryMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(missing))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(aggregate)));
    }

    @Test
    @DisplayName("saveAll throws when the department does not exist")
    void saveAllThrowsWhenDepartmentMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(Department.builder().id(missing).name("Missing").build())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(aggregate)));
    }

    @Test
    @DisplayName("saveAll throws when the role does not exist")
    void saveAllThrowsWhenRoleMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(Role.builder().id(missing).name("Missing").build())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(aggregate)));
    }

    @Test
    @DisplayName("saveAll persists multiple aggregates in one call")
    void saveAllPersistsMultiple() {
        PayrollPeriodAggregate first = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(2)
                .build();
        PayrollPeriodAggregate second = PayrollPeriodAggregate.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(DE))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.PART_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build();

        adapter.saveAll(List.of(first, second));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodAggregate> result = adapter.findByPayrollPeriodId(FEBRUARY);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByPayrollPeriodId returns the seeded aggregates for a period")
    void findByPayrollPeriodIdReturnsSeeded() {
        List<PayrollPeriodAggregate> result = adapter.findByPayrollPeriodId(JANUARY);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(AGG_FR)));
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(AGG_DE)));
    }

    @Test
    @DisplayName("findByPayrollPeriodId returns empty for a period with no rows")
    void findByPayrollPeriodIdReturnsEmptyWhenNone() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findByPayrollPeriodId(missing).isEmpty());
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        PayrollPeriodAggregateCriteria criteria = PayrollPeriodAggregateCriteria.builder()
                .payrollPeriodId(JANUARY)
                .sortBy("employeeCount")
                .sortDirection(SortDirection.DESC)
                .build();

        List<PayrollPeriodAggregate> result = adapter.list(criteria);

        assertEquals(2, result.size());
        assertEquals(AGG_FR, result.get(0).getId());
        assertEquals(AGG_DE, result.get(1).getId());
    }

    @Test
    @DisplayName("list filters by payroll period id")
    void listFiltersByPayrollPeriodId() {
        PayrollPeriodAggregateCriteria criteria = PayrollPeriodAggregateCriteria.builder()
                .payrollPeriodId(JANUARY)
                .build();

        List<PayrollPeriodAggregate> result = adapter.list(criteria);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("list filters by status")
    void listFiltersByStatus() {
        PayrollPeriodAggregateCriteria criteria = PayrollPeriodAggregateCriteria.builder()
                .status(PayrollSummaryStatus.VALIDATED)
                .build();

        List<PayrollPeriodAggregate> result = adapter.list(criteria);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("deleteByPayrollPeriodId removes all rows for a period")
    void deleteByPayrollPeriodIdRemovesAll() {
        adapter.deleteByPayrollPeriodId(JANUARY);
        entityManager.flush();
        entityManager.clear();

        assertTrue(jpaRepository.findByPayrollPeriodId(JANUARY).isEmpty());
        assertEquals(0, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteByPayrollPeriodId is a no-op for a period with no rows")
    void deleteByPayrollPeriodIdIsNoOpWhenNone() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteByPayrollPeriodId(missing);

        assertEquals(2, jpaRepository.count());
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

    private Country country(UUID id) {
        return Country.builder()
                .id(id)
                .isoCode("XX")
                .name("Placeholder")
                .currencyCode("XXX")
                .build();
    }

    private Department department() {
        return Department.builder()
                .id(ENGINEERING)
                .name("Engineering")
                .build();
    }

    private Role role() {
        return Role.builder()
                .id(ENGINEER)
                .name("Engineer")
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