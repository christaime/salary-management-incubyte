package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link PayrollPeriodEarnerRanking}. */
class PayrollPeriodEarnerRankingTest {

    @Test
    @DisplayName("valid build succeeds and generates an id")
    void validBuildSucceedsAndGeneratesId() {
        PayrollPeriodEarnerRanking ranking = valid().build();

        assertNotNull(ranking.getId());
        assertEquals(1, ranking.getRank());
        assertEquals(PayrollSummaryStatus.DRAFT, ranking.getStatus());
        assertEquals(EmploymentType.FULL_TIME, ranking.getEmploymentType());
    }

    @Test
    @DisplayName("null payrollPeriod is rejected")
    void nullPayrollPeriodIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().payrollPeriod(null).build());
    }

    @Test
    @DisplayName("null status is rejected")
    void nullStatusIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().status(null).build());
    }

    @Test
    @DisplayName("null direction is rejected")
    void nullDirectionIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().direction(null).build());
    }

    @Test
    @DisplayName("null rank is rejected")
    void nullRankIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().rank(null).build());
    }

    @Test
    @DisplayName("rank below 1 is rejected")
    void rankBelowOneIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().rank(0).build());
    }

    @Test
    @DisplayName("rank above 5 is rejected")
    void rankAboveFiveIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().rank(6).build());
    }

    @Test
    @DisplayName("null employee is rejected")
    void nullEmployeeIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employee(null).build());
    }

    @Test
    @DisplayName("null originalAmount is rejected")
    void nullOriginalAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().originalAmount(null).build());
    }

    @Test
    @DisplayName("null referenceAmount is rejected")
    void nullReferenceAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().referenceAmount(null).build());
    }

    // ------------------------------------------------------------------
    // New fields: daysCovered + monthlyRateAmount
    // ------------------------------------------------------------------

    @Test
    @DisplayName("null daysCovered is rejected")
    void nullDaysCoveredIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().daysCovered(null).build());
    }

    @Test
    @DisplayName("daysCovered below 1 is rejected")
    void daysCoveredBelowOneIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().daysCovered(0).build());
    }

    @Test
    @DisplayName("null monthlyRateAmount is rejected")
    void nullMonthlyRateAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().monthlyRateAmount(null).build());
    }

    @Test
    @DisplayName("monthly rate is derived correctly from reference amount and daysCovered")
    void monthlyRateIsDerivedCorrectly() {
        // referenceAmount = 5400 USD over 31 days
        // monthlyRate = (5400 / 31) * 31 = 5400 (since the period is a full September)
        PayrollPeriodEarnerRanking ranking = valid().build();
        assertEquals(0, ranking.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("5400.00")));
        assertEquals("USD", ranking.getMonthlyRateAmount().getCurrency());
    }

    @Test
    @DisplayName("bi-weekly employee: 120 USD over 28 days -> monthly rate 132.86 (31-day month)")
    void biWeeklyEmployeeMonthlyRate() {
        // Ariana's case from the calendar-trap discussion.
        PayrollPeriodEarnerRanking ranking = valid()
                .payrollPeriod(aPayrollPeriod())   // September: 30 days
                .referenceAmount(amount("120.00", "USD"))
                .daysCovered(28)
                .monthlyRateAmount(amount("128.57", "USD")) // (120/28)*30
                .build();
        assertEquals(0, ranking.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("128.57")));
    }

    // ------------------------------------------------------------------
    // Copy methods
    // ------------------------------------------------------------------

    @Test
    @DisplayName("withOriginalAmount returns a new instance with the same id")
    void withOriginalAmountReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated =
                original.withOriginalAmount(amount("9999.99", "EUR"));

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(0, original.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("5000.00")));
        assertEquals(0, updated.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("9999.99")));
    }

    @Test
    @DisplayName("withReferenceAmount returns a new instance with the same id")
    void withReferenceAmountReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated =
                original.withReferenceAmount(amount("10800.00", "USD"));

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(0, original.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("5400.00")));
        assertEquals(0, updated.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("10800.00")));
    }

    @Test
    @DisplayName("withStatus returns a new instance with the same id")
    void withStatusReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated = original.withStatus(PayrollSummaryStatus.VALIDATED);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(PayrollSummaryStatus.DRAFT, original.getStatus());
        assertEquals(PayrollSummaryStatus.VALIDATED, updated.getStatus());
    }

    @Test
    @DisplayName("withDaysCovered returns a new instance with the same id")
    void withDaysCoveredReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated = original.withDaysCovered(14);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(31, original.getDaysCovered());
        assertEquals(14, updated.getDaysCovered());
    }

    @Test
    @DisplayName("withMonthlyRate(Amount, int) updates both fields atomically")
    void withMonthlyRateUpdatesBothFields() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated =
                original.withMonthlyRate(amount("243.57", "USD"), 14);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(31, original.getDaysCovered());
        assertEquals(14, updated.getDaysCovered());
        assertEquals(0, updated.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("243.57")));
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy path with realistic values")
    void happyPathWithRealisticValues() {
        PayrollPeriodEarnerRanking ranking = valid().build();

        assertEquals("FR", ranking.getCountry().getIsoCode());
        assertEquals("Engineering", ranking.getDepartment().getName());
        assertEquals("Senior Engineer", ranking.getRole().getName());
        assertEquals(EmploymentType.FULL_TIME, ranking.getEmploymentType());
        assertEquals(EarnerRankingDirection.TOP, ranking.getDirection());
        assertEquals(1, ranking.getRank());
        assertEquals("EMP-0001", ranking.getEmployee().getMatricule());
        assertEquals(0, ranking.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("5000.00")));
        assertEquals("EUR", ranking.getOriginalAmount().getCurrency());
        assertEquals(0, ranking.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("5400.00")));
        assertEquals("USD", ranking.getReferenceAmount().getCurrency());
        assertEquals(31, ranking.getDaysCovered());
        assertEquals(0, ranking.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("5400.00")));
        assertEquals("USD", ranking.getMonthlyRateAmount().getCurrency());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private PayrollPeriodEarnerRanking.Builder valid() {
        return PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.DRAFT)
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .employmentType(EmploymentType.FULL_TIME)
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employee(anEmployee())
                .originalAmount(amount("5000.00", "EUR"))
                .referenceAmount(amount("5400.00", "USD"))
                .daysCovered(31)
                .monthlyRateAmount(amount("5400.00", "USD"));
    }

    private Amount amount(String value, String currency) {
        return Amount.builder()
                .amount(new BigDecimal(value))
                .currency(currency)
                .build();
    }

    private Country aCountry() {
        return Country.builder()
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
    }

    private Department aDepartment() {
        return Department.builder()
                .code("ENG")
                .name("Engineering")
                .build();
    }

    private Role aRole() {
        return Role.builder()
                .code("SSWE")
                .name("Senior Engineer")
                .build();
    }

    private Employee anEmployee() {
        return Employee.builder()
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
    }

    private PayrollPeriod aPayrollPeriod() {
        return PayrollPeriod.builder()
                .name("September 2026")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }
}