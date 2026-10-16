package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.values.Amounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

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

    private static PayrollPeriodAggregate valid() {
        return PayrollPeriodAggregate.builder()
                .payrollPeriodId(UUID.randomUUID())
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
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
    }

    @Test
    @DisplayName("Zero employee count is allowed")
    void zeroEmployeeCountAllowed() {
        PayrollPeriodAggregate aggregate = PayrollPeriodAggregate.builder()
                .payrollPeriodId(UUID.randomUUID())
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
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
                .payrollPeriodId(UUID.randomUUID())
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .originalAmounts(amounts("USD"))
                .referenceAmounts(amounts("EUR"))
                .employeeCount(-1)
                .build());
    }

    @Test
    @DisplayName("Missing originalAmounts is rejected")
    void missingOriginalAmountsRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodAggregate.builder()
                .payrollPeriodId(UUID.randomUUID())
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
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