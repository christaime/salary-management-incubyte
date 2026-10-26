package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amount;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    /** Seed counts, kept as constants so the expectations are obvious. */
    private static final int SEEDED_RANKINGS = 12;      // 6 partition kinds × 2 employees
    private static final int SEEDED_PERCENTILES = 42;   // 6 partition kinds × 7 points

    @Autowired
    private PayrollPeriodEarnerRankingRepositoryAdapter adapter;

    @Autowired
    private PayrollPeriodEarnerRankingJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // saveAll (rankings)
    // ------------------------------------------------------------------

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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
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
        assertEquals(0, result.get(0).getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("5000.00")));
        assertEquals("EUR", result.get(0).getOriginalAmount().getCurrency());
        assertEquals(0, result.get(0).getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("5376.34")));
        assertEquals("USD", result.get(0).getReferenceAmount().getCurrency());
        assertEquals(31, result.get(0).getDaysCovered());
        assertEquals(0, result.get(0).getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("5376.34")));
        assertEquals("USD", result.get(0).getMonthlyRateAmount().getCurrency());
    }

    @Test
    @DisplayName("saveAll throws when the payroll period does not exist")
    void saveAllThrowsWhenPayrollPeriodMissing() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999999");
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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the employee does not exist")
    void saveAllThrowsWhenEmployeeMissing() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999999");
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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the country does not exist")
    void saveAllThrowsWhenCountryMissing() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999999");
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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the department does not exist")
    void saveAllThrowsWhenDepartmentMissing() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(Department.builder()
                        .id(missing).code("MSS").name("Missing").build())
                .role(role())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.saveAll(List.of(ranking)));
    }

    @Test
    @DisplayName("saveAll throws when the role does not exist")
    void saveAllThrowsWhenRoleMissing() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999999");
        PayrollPeriodEarnerRanking ranking = PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(FR))
                .department(department())
                .role(Role.builder().id(missing).code("MSS").name("Missing").build())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
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
                .originalAmount(amount("1200.00", "EUR"))
                .referenceAmount(amount("1290.32", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("1290.32", "USD"))
                .build();

        adapter.saveAll(List.of(top, bottom));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodEarnerRanking> result = adapter.findByPayrollPeriodId(FEBRUARY);

        assertEquals(2, result.size());
    }

    // ------------------------------------------------------------------
    // findByPayrollPeriodId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findByPayrollPeriodId returns the seeded rankings for a period")
    void findByPayrollPeriodIdReturnsSeeded() {
        List<PayrollPeriodEarnerRanking> result = adapter.findByPayrollPeriodId(JANUARY);

        assertEquals(SEEDED_RANKINGS, result.size());
        assertTrue(result.stream()
                .allMatch(r -> r.getPayrollPeriod().getId().equals(JANUARY)));
    }

    @Test
    @DisplayName("findByPayrollPeriodId returns empty for a period with no rows")
    void findByPayrollPeriodIdReturnsEmptyWhenNone() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");

        assertTrue(adapter.findByPayrollPeriodId(missing).isEmpty());
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------
    @Test
    @DisplayName("list returns all seeded rows for the period")
    void listReturnsMatchingRowsSorted() {
        PayrollPeriodEarnerRankingCriteria criteria =
                PayrollPeriodEarnerRankingCriteria.builder()
                        .payrollPeriodId(JANUARY)
                        .build();

        List<PayrollPeriodEarnerRanking> result = adapter.list(criteria);

        assertEquals(SEEDED_RANKINGS, result.size());

        // 6 FR partition kinds carry 5376.34; 6 DE partition kinds carry 1290.32.
        long frRows = result.stream()
                .filter(r -> r.getReferenceAmount().getAmount()
                        .compareTo(new BigDecimal("5376.34")) == 0)
                .count();
        long deRows = result.stream()
                .filter(r -> r.getReferenceAmount().getAmount()
                        .compareTo(new BigDecimal("1290.32")) == 0)
                .count();

        assertEquals(6, frRows);
        assertEquals(6, deRows);
    }

    @Test
    @DisplayName("list filters by payroll period id")
    void listFiltersByPayrollPeriodId() {
        PayrollPeriodEarnerRankingCriteria criteria =
                PayrollPeriodEarnerRankingCriteria.builder()
                        .payrollPeriodId(JANUARY)
                        .build();

        List<PayrollPeriodEarnerRanking> result = adapter.list(criteria);

        assertEquals(SEEDED_RANKINGS, result.size());
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

    // ------------------------------------------------------------------
    // deleteDraftsByPayrollPeriodId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deleteDraftsByPayrollPeriodId removes all DRAFT rows for a period")
    void deleteDraftsByPayrollPeriodIdRemovesAll() {
        adapter.deleteDraftsByPayrollPeriodId(JANUARY);
        entityManager.flush();
        entityManager.clear();

        assertTrue(jpaRepository.findByPayrollPeriodId(JANUARY).isEmpty());
        assertEquals(0, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteDraftsByPayrollPeriodId is a no-op for a period with no rows")
    void deleteDraftsByPayrollPeriodIdIsNoOpWhenNone() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");

        adapter.deleteDraftsByPayrollPeriodId(missing);

        assertEquals(SEEDED_RANKINGS, jpaRepository.count());
    }

    // ------------------------------------------------------------------
    // Percentile — findByPayrollPeriodId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findPercentileByPayrollPeriodId returns the seeded points for a period")
    void findPercentileByPayrollPeriodIdReturnsSeeded() {
        List<PayrollPeriodEarnerPercentile> result =
                adapter.findPercentileByPayrollPeriodId(JANUARY);

        assertEquals(SEEDED_PERCENTILES, result.size());

        List<Integer> points = result.stream()
                .map(PayrollPeriodEarnerPercentile::getPercentile)
                .sorted()
                .toList();
        // 6 partition kinds × 7 points, so each label appears 6 times.
        assertEquals(6, points.stream().filter(p -> p == 0).count());
        assertEquals(6, points.stream().filter(p -> p == 10).count());
        assertEquals(6, points.stream().filter(p -> p == 25).count());
        assertEquals(6, points.stream().filter(p -> p == 50).count());
        assertEquals(6, points.stream().filter(p -> p == 75).count());
        assertEquals(6, points.stream().filter(p -> p == 90).count());
        assertEquals(6, points.stream().filter(p -> p == 100).count());
    }

    @Test
    @DisplayName("findPercentileByPayrollPeriodId returns all six partition kinds")
    void findPercentileByPayrollPeriodIdReturnsAllKinds() {
        List<PayrollPeriodEarnerPercentile> result =
                adapter.findPercentileByPayrollPeriodId(JANUARY);

        // FULL: all four set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() != null && p.getDepartment() != null
                        && p.getRole() != null && p.getEmploymentType() != null).count());

        // COUNTRY: only country set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() != null && p.getDepartment() == null
                        && p.getRole() == null && p.getEmploymentType() == null).count());

        // DEPARTMENT: only department set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() == null && p.getDepartment() != null
                        && p.getRole() == null && p.getEmploymentType() == null).count());

        // ROLE: only role set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() == null && p.getDepartment() == null
                        && p.getRole() != null && p.getEmploymentType() == null).count());

        // EMPLOYMENT_TYPE: only employment type set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() == null && p.getDepartment() == null
                        && p.getRole() == null && p.getEmploymentType() != null).count());

        // GLOBAL: none set
        assertEquals(7, result.stream().filter(p ->
                p.getCountry() == null && p.getDepartment() == null
                        && p.getRole() == null && p.getEmploymentType() == null).count());
    }

    @Test
    @DisplayName("findPercentileByPayrollPeriodId maps amounts and population correctly")
    void findPercentileByPayrollPeriodIdMapsAmountsAndPopulation() {
        List<PayrollPeriodEarnerPercentile> result =
                adapter.findPercentileByPayrollPeriodId(JANUARY);

        PayrollPeriodEarnerPercentile p0 = result.stream()
                .filter(p -> p.getPercentile() == 0)
                .findFirst().orElseThrow();
        assertEquals(0, p0.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("1290.32")));
        assertEquals("USD", p0.getReferenceAmount().getCurrency());
        assertEquals(2, p0.getEmployeeCount());
        assertEquals(PayrollSummaryStatus.DRAFT, p0.getStatus());
        assertEquals(JANUARY, p0.getPayrollPeriod().getId());

        PayrollPeriodEarnerPercentile p100 = result.stream()
                .filter(p -> p.getPercentile() == 100)
                .findFirst().orElseThrow();
        assertEquals(0, p100.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("5376.34")));
        assertEquals("USD", p100.getReferenceAmount().getCurrency());
        assertEquals(2, p100.getEmployeeCount());
    }

    @Test
    @DisplayName("findPercentileByPayrollPeriodId returns empty for a period with no rows")
    void findPercentileByPayrollPeriodIdReturnsEmptyWhenNone() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");

        assertTrue(adapter.findPercentileByPayrollPeriodId(missing).isEmpty());
    }

    // ------------------------------------------------------------------
    // saveAll (rankings + percentiles together)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("saveAll(rankings, percentiles) persists both lists")
    void saveAllWithPercentilesPersistsBoth() {
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
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        PayrollPeriodEarnerPercentile p50 = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .status(PayrollSummaryStatus.DRAFT)
                .percentile(50)
                .referenceAmount(amount("60000.00", "USD"))
                .employeeCount(3)
                .build();

        adapter.saveAll(List.of(ranking), List.of(p50));
        entityManager.flush();
        entityManager.clear();

        List<PayrollPeriodEarnerRanking> rankings = adapter.findByPayrollPeriodId(FEBRUARY);
        assertEquals(1, rankings.size());
        assertEquals(ranking.getId(), rankings.get(0).getId());

        List<PayrollPeriodEarnerPercentile> percentiles =
                adapter.findPercentileByPayrollPeriodId(FEBRUARY);
        assertEquals(1, percentiles.size());
        assertEquals(p50.getId(), percentiles.get(0).getId());
        assertEquals(50, percentiles.get(0).getPercentile());
        assertEquals(0, percentiles.get(0).getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("60000.00")));
        assertEquals(3, percentiles.get(0).getEmployeeCount());
    }

    @Test
    @DisplayName("saveAll(rankings, percentiles) throws when the percentile's payroll period is missing")
    void saveAllWithPercentilesThrowsWhenPercentilePeriodMissing() {
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
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");
        PayrollPeriodEarnerPercentile p50 = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(payrollPeriod(missing, "Missing",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .percentile(50)
                .referenceAmount(amount("60000.00", "USD"))
                .employeeCount(3)
                .build();

        assertThrows(EntityNotFoundException.class,
                () -> adapter.saveAll(List.of(ranking), List.of(p50)));
    }

    @Test
    @DisplayName("saveAll(rankings, percentiles) throws when the percentile's country is missing")
    void saveAllWithPercentilesThrowsWhenPercentileCountryMissing() {
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
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");
        PayrollPeriodEarnerPercentile p50 = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .country(country(missing))
                .percentile(50)
                .referenceAmount(amount("60000.00", "USD"))
                .employeeCount(3)
                .build();

        assertThrows(EntityNotFoundException.class,
                () -> adapter.saveAll(List.of(ranking), List.of(p50)));
    }

    @Test
    @DisplayName("saveAll(rankings, percentiles) throws when the percentile's department is missing")
    void saveAllWithPercentilesThrowsWhenPercentileDepartmentMissing() {
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
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");
        PayrollPeriodEarnerPercentile p50 = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .department(Department.builder()
                        .id(missing).code("MSS").name("Missing").build())
                .percentile(50)
                .referenceAmount(amount("60000.00", "USD"))
                .employeeCount(3)
                .build();

        assertThrows(EntityNotFoundException.class,
                () -> adapter.saveAll(List.of(ranking), List.of(p50)));
    }

    @Test
    @DisplayName("saveAll(rankings, percentiles) throws when the percentile's role is missing")
    void saveAllWithPercentilesThrowsWhenPercentileRoleMissing() {
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
                .employee(employee(ADA))
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5376.34", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5376.34", "USD"))
                .build();

        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");
        PayrollPeriodEarnerPercentile p50 = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .status(PayrollSummaryStatus.DRAFT)
                .role(Role.builder().id(missing).code("MSS").name("Missing").build())
                .percentile(50)
                .referenceAmount(amount("60000.00", "USD"))
                .employeeCount(3)
                .build();

        assertThrows(EntityNotFoundException.class,
                () -> adapter.saveAll(List.of(ranking), List.of(p50)));
    }

    // ------------------------------------------------------------------
    // Percentile — deleteDraftsPercentileByPayrollPeriodId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deleteDraftsPercentileByPayrollPeriodId removes all DRAFT percentile rows for a period")
    void deleteDraftsPercentileByPayrollPeriodIdRemovesAll() {
        assertEquals(SEEDED_PERCENTILES,
                adapter.findPercentileByPayrollPeriodId(JANUARY).size());

        adapter.deleteDraftsPercentileByPayrollPeriodId(JANUARY);
        entityManager.flush();
        entityManager.clear();

        assertTrue(adapter.findPercentileByPayrollPeriodId(JANUARY).isEmpty());
    }

    @Test
    @DisplayName("deleteDraftsPercentileByPayrollPeriodId is a no-op for a period with no rows")
    void deleteDraftsPercentileByPayrollPeriodIdIsNoOpWhenNone() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");

        adapter.deleteDraftsPercentileByPayrollPeriodId(missing);

        assertEquals(SEEDED_PERCENTILES,
                adapter.findPercentileByPayrollPeriodId(JANUARY).size());
    }

    // ------------------------------------------------------------------
    // findByPayrollPeriodAndDimension
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findByPayrollPeriodAndDimension returns only the rows of the given dimension")
    void findByPayrollPeriodAndDimensionFilters() {
        List<PayrollPeriodEarnerRanking> all = adapter.findByPayrollPeriodId(JANUARY);
        List<PayrollPeriodEarnerRanking> fr = adapter.findByPayrollPeriodAndDimension(
                JANUARY, FR, null, null, null);
        List<PayrollPeriodEarnerRanking> global = adapter.findByPayrollPeriodAndDimension(
                JANUARY, null, null, null, null);

        assertEquals(SEEDED_RANKINGS, all.size());

        // COUNTRY/FR: 1 row (the country-scoped TOP for ADA; GRACE is in DE).
        assertEquals(1, fr.size());
        assertEquals(FR, fr.get(0).getCountry().getId());
        assertNull(fr.get(0).getDepartment());
        assertNull(fr.get(0).getRole());
        assertNull(fr.get(0).getEmploymentType());

        // GLOBAL: 2 rows (TOP for ADA + BOTTOM for GRACE).
        assertEquals(2, global.size());
        assertTrue(global.stream().allMatch(r -> r.getCountry() == null));
    }

    @Test
    @DisplayName("findPercentileByPayrollPeriodAndDimension returns only the rows of the given dimension")
    void findPercentileByPayrollPeriodAndDimensionFilters() {
        List<PayrollPeriodEarnerPercentile> global = adapter
                .findPercentileByPayrollPeriodAndDimension(JANUARY, null, null, null, null);
        List<PayrollPeriodEarnerPercentile> fr = adapter
                .findPercentileByPayrollPeriodAndDimension(JANUARY, FR, null, null, null);

        // GLOBAL: 7 rows.
        assertEquals(7, global.size());
        assertTrue(global.stream().allMatch(p -> p.getCountry() == null));

        // COUNTRY/FR: 7 rows.
        assertEquals(7, fr.size());
        assertTrue(fr.stream().allMatch(p -> p.getCountry().getId().equals(FR)));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

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
                .code("ENG")
                .name("Engineering")
                .build();
    }

    private Role role() {
        return Role.builder()
                .id(ENGINEER)
                .code("SWE")
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

    private Amount amount(String value, String currency) {
        return Amount.builder()
                .amount(new BigDecimal(value))
                .currency(currency)
                .build();
    }
}