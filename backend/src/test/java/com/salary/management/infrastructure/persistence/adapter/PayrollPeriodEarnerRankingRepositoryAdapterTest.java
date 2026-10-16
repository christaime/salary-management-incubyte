package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodEarnerRankingJpaRepository;
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

/** Integration tests for {@link PayrollPeriodEarnerRankingRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/payroll-period-earner-ranking-adapter.sql")
class PayrollPeriodEarnerRankingRepositoryAdapterTest {

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
    private static final UUID RANK_FR =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID RANK_DE =
            UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private PayrollPeriodEarnerRankingRepositoryAdapter adapter;

    @Autowired
    private PayrollPeriodEarnerRankingJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("saveAll then findByPayrollPeriodId returns the saved rankings")
    void saveAllThenFindByPayrollPeriodIdReturnsSaved() {
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();

        adapter.saveAll(List.of(ranking));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodEarnerRanking> result = adapter.findByPayrollPeriodId(FEBRUARY);

        assertEquals(1, result.size());
        assertEquals(ranking.getId(), result.get(0).getId());
        assertEquals(FR, result.get(0).getCountry().getId());
        assertEquals(ADA, result.get(0).getEmployee().getId());
        assertEquals(EarnerRankingDirection.TOP, result.get(0).getDirection());
        assertEquals(1, result.get(0).getRank());
        assertEquals(0, result.get(0).getAmount().compareTo(new BigDecimal("5000.00")));
    }

    @Test
    @DisplayName("saveAll throws when the payroll period does not exist")
    void saveAllThrowsWhenPayrollPeriodMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(missing, "Missing",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the employee does not exist")
    void saveAllThrowsWhenEmployeeMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(missing))
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the country does not exist")
    void saveAllThrowsWhenCountryMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(missing))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the department does not exist")
    void saveAllThrowsWhenDepartmentMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(Department.builder().id(missing).name("Missing").build())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the role does not exist")
    void saveAllThrowsWhenRoleMissing() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(Role.builder().id(missing).name("Missing").build())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll persists multiple rankings in one call")
    void saveAllPersistsMultiple() {
        PayrollPeriodEarnerRanking top = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .amount(new BigDecimal("5000.00"))
                .build();
        PayrollPeriodEarnerRanking bottom = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(DE))
                .department(department())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.BOTTOM)
                .rank(1)
                .employee(employee(GRACE))
                .amount(new BigDecimal("1200.00"))
                .build();

        adapter.saveAll(List.of(top, bottom));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodEarnerRanking> result = adapter.findByPayrollPeriodId(FEBRUARY);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByPayrollPeriodId returns the seeded rankings for a period")
    void findByPayrollPeriodIdReturnsSeeded() {
        List<PayrollPeriodEarnerRanking> result = adapter.findByPayrollPeriodId(JANUARY);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(RANK_FR)));
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(RANK_DE)));
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
        PayrollPeriodEarnerRankingCriteria criteria =
                PayrollPeriodEarnerRankingCriteria.builder()
                        .payrollPeriodId(JANUARY)
                        .sortBy("amount")
                        .sortDirection(SortDirection.DESC)
                        .build();

        List<PayrollPeriodEarnerRanking> result = adapter.list(criteria);

        assertEquals(2, result.size());
        assertEquals(RANK_FR, result.get(0).getId());
        assertEquals(RANK_DE, result.get(1).getId());
    }

    @Test
    @DisplayName("list filters by payroll period id")
    void listFiltersByPayrollPeriodId() {
        PayrollPeriodEarnerRankingCriteria criteria =
                PayrollPeriodEarnerRankingCriteria.builder()
                        .payrollPeriodId(JANUARY)
                        .build();

        List<PayrollPeriodEarnerRanking> result = adapter.list(criteria);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("list filters by status")
    void listFiltersByStatus() {
        PayrollPeriodEarnerRankingCriteria criteria =
                PayrollPeriodEarnerRankingCriteria.builder()
                        .status(PayrollSummaryStatus.VALIDATED)
                        .build();

        List<PayrollPeriodEarnerRanking> result = adapter.list(criteria);

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

    private Employee employee(UUID id) {
        return Employee.builder()
                .id(id)
                .matricule("PLACEHOLDER")
                .name("Placeholder")
                .email("placeholder@example.com")
                .build();
    }
}