package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.values.Amounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPeriodAggregateTest {

    private static Amounts amounts(String currency) {
        return Amounts.builder()
                .base(new BigDecimal("1000"))
                .variable(new BigDecimal("200"))
                .allowances(new BigDecimal("50"))
                .employerTaxes(new BigDecimal("150"))
                .currency(currency)
                .build();
    }

    private static PayrollPeriod aPayrollPeriod() {
        return PayrollPeriod.builder()
                .name("2024-01 Monthly")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private static Country aCountry() {
        return Country.builder().isoCode("FR").name("France").currencyCode("EUR").build();
    }

    private static Department aDepartment() {
        return Department.builder().name("Engineering").build();
    }

    private static Role aRole() {
        return Role.builder().name("Engineer").build();
    }

    private static PayrollPeriodAggregate valid() {
        return PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .originalAmounts(amounts("USD"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(10)
                .build();
    }

    @Test
    @DisplayName("Valid aggregate is constructed and id is generated")
    void validConstruction() {
        PayrollPeriodAggregate aggregate = valid();
        assertNotNull(aggregate.getId());
        assertEquals(10, aggregate.getEmployeeCount());
        assertEquals("USD", aggregate.getOriginalAmounts().getCurrency());
        assertEquals("EUR", aggregate.getReferenceAmounts().getCurrency());
        assertEquals("FR", aggregate.getCountry().getIsoCode());
        assertEquals("Engineering", aggregate.getDepartment().getName());
        assertEquals("Engineer", aggregate.getRole().getName());
    }

    @Test
    @DisplayName("Zero employee count is allowed")
    void zeroEmployeeCountAllowed() {
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .originalAmounts(amounts("USD"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(0)
                .build();
        assertEquals(0, aggregate.getEmployeeCount());
    }

    @Test
    @DisplayName("Negative employee count is rejected")
    void negativeEmployeeCountRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .originalAmounts(amounts("USD"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(-1)
                .build());
    }

    @Test
    @DisplayName("Missing originalAmounts is rejected")
    void missingOriginalAmountsRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build());
    }

    @Test
    @DisplayName("Missing country is rejected")
    void missingCountryRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodAggregate.builder()
                .payrollPeriod(aPayrollPeriod())
                .department(aDepartment())
                .role(aRole())
                .originalAmounts(amounts("USD"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(1)
                .build());
    }

    @Test
    @DisplayName("withEmployeeCount returns a new instance")
    void withEmployeeCountTransition() {
        PayrollPeriodAggregate original = valid();
        PayrollPeriodAggregate updated = original.withEmployeeCount(20);

        assertNotSame(original, updated);
        assertEquals(10, original.getEmployeeCount());
        assertEquals(20, updated.getEmployeeCount());
    }

    @Test
    @DisplayName("withEmployeeCount rejects negative input")
    void withEmployeeCountRejectsInvalid() {
        assertThrows(DomainValidationException.class,
                () -> valid().withEmployeeCount(-5));
    }
}