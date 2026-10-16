package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link PayrollPeriodAggregate}. */
class PayrollPeriodAggregateTest {

    @Test
    @DisplayName("valid build succeeds and generates an id")
    void validBuildSucceedsAndGeneratesId() {
        PayrollPeriodAggregate aggregate = valid().build();

        assertNotNull(aggregate.getId());
        assertEquals(42, aggregate.getEmployeeCount());
        assertEquals(PayrollSummaryStatus.DRAFT, aggregate.getStatus());
        assertEquals(EmploymentType.FULL_TIME, aggregate.getEmploymentType());
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
    @DisplayName("null originalAmounts is rejected")
    void nullOriginalAmountsIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().originalAmounts(null).build());
    }

    @Test
    @DisplayName("null referenceAmounts is rejected")
    void nullReferenceAmountsIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().referenceAmounts(null).build());
    }

    @Test
    @DisplayName("null employeeCount is rejected")
    void nullEmployeeCountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employeeCount(null).build());
    }

    @Test
    @DisplayName("negative employeeCount is rejected")
    void negativeEmployeeCountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employeeCount(-1).build());
    }

    @Test
    @DisplayName("withEmployeeCount returns a new instance with the same id")
    void withEmployeeCountReturnsNewInstanceWithSameId() {
        PayrollPeriodAggregate original = valid().build();
        PayrollPeriodAggregate updated = original.withEmployeeCount(50);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(42, original.getEmployeeCount());
        assertEquals(50, updated.getEmployeeCount());
    }

    @Test
    @DisplayName("withStatus returns a new instance with the same id")
    void withStatusReturnsNewInstanceWithSameId() {
        PayrollPeriodAggregate original = valid().build();
        PayrollPeriodAggregate updated = original.withStatus(PayrollSummaryStatus.VALIDATED);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(PayrollSummaryStatus.DRAFT, original.getStatus());
        assertEquals(PayrollSummaryStatus.VALIDATED, updated.getStatus());
    }

    @Test
    @DisplayName("happy path with realistic values")
    void happyPathWithRealisticValues() {
        PayrollPeriodAggregate aggregate = valid().build();

        assertEquals("FR", aggregate.getCountry().getIsoCode());
        assertEquals("Engineering", aggregate.getDepartment().getName());
        assertEquals("Senior Engineer", aggregate.getRole().getName());
        assertEquals(EmploymentType.FULL_TIME, aggregate.getEmploymentType());
        assertEquals("EUR", aggregate.getOriginalAmounts().getCurrency());
        assertEquals("EUR", aggregate.getReferenceAmounts().getCurrency());
    }

    private PayrollPeriodAggregate.Builder valid() {
        return PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.DRAFT)
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .employmentType(EmploymentType.FULL_TIME)
                .originalAmounts(amounts("EUR"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(42);
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
                .name("Engineering")
                .build();
    }

    private Role aRole() {
        return Role.builder()
                .name("Senior Engineer")
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

    private Amounts amounts(String currency) {
        return Amounts.builder()
                .base(new BigDecimal("1000.00"))
                .variable(new BigDecimal("200.00"))
                .allowances(new BigDecimal("50.00"))
                .employerTaxes(new BigDecimal("150.00"))
                .currency(currency)
                .build();
    }
}