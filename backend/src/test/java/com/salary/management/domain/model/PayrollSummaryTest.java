package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollSummaryTest {

    private static Amounts validAmounts() {
        return Amounts.builder()
                .base(new BigDecimal("1000"))
                .variable(new BigDecimal("200"))
                .allowances(new BigDecimal("50"))
                .employerTaxes(new BigDecimal("150"))
                .currency("EUR")
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

    private static PayrollSummary valid() {
        return PayrollSummary.builder()
                .employeeId(UUID.randomUUID())
                .payrollPeriod(aPayrollPeriod())
                .employeePeriodicAssignmentId(UUID.randomUUID())
                .amounts(validAmounts())
                .status(PayrollSummaryStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("Valid payroll summary is constructed and id is generated")
    void validConstruction() {
        PayrollSummary summary = valid();
        assertNotNull(summary.getId());
        assertEquals(PayrollSummaryStatus.DRAFT, summary.getStatus());
        assertEquals(new BigDecimal("1400"), summary.getAmounts().getTotal());
        assertEquals("2024-01 Monthly", summary.getPayrollPeriod().getName());
    }

    @Test
    @DisplayName("Missing amounts is rejected")
    void missingAmountsRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollSummary.builder()
                .employeeId(UUID.randomUUID())
                .payrollPeriod(aPayrollPeriod())
                .employeePeriodicAssignmentId(UUID.randomUUID())
                .status(PayrollSummaryStatus.DRAFT)
                .build());
    }

    @Test
    @DisplayName("Missing status is rejected")
    void missingStatusRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollSummary.builder()
                .employeeId(UUID.randomUUID())
                .payrollPeriod(aPayrollPeriod())
                .employeePeriodicAssignmentId(UUID.randomUUID())
                .amounts(validAmounts())
                .build());
    }

    @Test
    @DisplayName("Missing employeeId is rejected")
    void missingEmployeeIdRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollSummary.builder()
                .payrollPeriod(aPayrollPeriod())
                .employeePeriodicAssignmentId(UUID.randomUUID())
                .amounts(validAmounts())
                .status(PayrollSummaryStatus.DRAFT)
                .build());
    }

    @Test
    @DisplayName("Missing payrollPeriod is rejected")
    void missingPayrollPeriodRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollSummary.builder()
                .employeeId(UUID.randomUUID())
                .employeePeriodicAssignmentId(UUID.randomUUID())
                .amounts(validAmounts())
                .status(PayrollSummaryStatus.DRAFT)
                .build());
    }

    @Test
    @DisplayName("withStatus returns a new instance")
    void withStatusTransition() {
        PayrollSummary original = valid();
        PayrollSummary updated = original.withStatus(PayrollSummaryStatus.VALIDATED);

        assertNotSame(original, updated);
        assertEquals(PayrollSummaryStatus.DRAFT, original.getStatus());
        assertEquals(PayrollSummaryStatus.VALIDATED, updated.getStatus());
    }

    @Test
    @DisplayName("withStatus rejects null")
    void withStatusRejectsNull() {
        assertThrows(DomainValidationException.class,
                () -> valid().withStatus(null));
    }

    @Test
    @DisplayName("withPayrollPeriod returns a new instance")
    void withPayrollPeriodTransition() {
        PayrollSummary original = valid();
        PayrollPeriod newPeriod = PayrollPeriod.builder()
                .name("2024-02 Monthly")
                .startDate(LocalDate.of(2024, 2, 1))
                .endDate(LocalDate.of(2024, 2, 29))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();

        PayrollSummary updated = original.withPayrollPeriod(newPeriod);

        assertNotSame(original, updated);
        assertEquals("2024-01 Monthly", original.getPayrollPeriod().getName());
        assertEquals("2024-02 Monthly", updated.getPayrollPeriod().getName());
    }
}