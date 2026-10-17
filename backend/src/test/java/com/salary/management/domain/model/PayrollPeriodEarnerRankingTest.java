package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
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
    @DisplayName("null country is rejected")
    void nullCountryIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().country(null).build());
    }

    @Test
    @DisplayName("null department is rejected")
    void nullDepartmentIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().department(null).build());
    }

    @Test
    @DisplayName("null role is rejected")
    void nullRoleIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().role(null).build());
    }

    @Test
    @DisplayName("null employmentType is rejected")
    void nullEmploymentTypeIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employmentType(null).build());
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
    @DisplayName("null amount is rejected")
    void nullAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().amount(null).build());
    }

    @Test
    @DisplayName("negative amount is rejected")
    void negativeAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().amount(new BigDecimal("-0.01")).build());
    }

    @Test
    @DisplayName("withAmount returns a new instance with the same id")
    void withAmountReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerRanking original = valid().build();
        PayrollPeriodEarnerRanking updated = original.withAmount(new BigDecimal("9999.99"));

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(0, original.getAmount().compareTo(new BigDecimal("5000.00")));
        assertEquals(0, updated.getAmount().compareTo(new BigDecimal("9999.99")));
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
    }

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
                .amount(new BigDecimal("5000.00"));
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