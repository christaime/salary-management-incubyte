package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPeriodSnapshotTest {

    private static PayrollPeriod aPayrollPeriod() {
        return PayrollPeriod.builder()
                .name("2024-01 Monthly")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private static PayrollPeriodSnapshot valid() {
        return PayrollPeriodSnapshot.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.DRAFT)
                .warnings(List.of("Missing timesheet for 2 employees"))
                .build();
    }

    @Test
    @DisplayName("Valid snapshot is constructed and id is generated")
    void validConstruction() {
        PayrollPeriodSnapshot snapshot = valid();
        assertNotNull(snapshot.getId());
        assertEquals(PayrollSummaryStatus.DRAFT, snapshot.getStatus());
        assertEquals(1, snapshot.getWarnings().size());
        assertEquals("2024-01 Monthly", snapshot.getPayrollPeriod().getName());
    }

    @Test
    @DisplayName("Empty warnings list is allowed")
    void emptyWarningsAllowed() {
        PayrollPeriodSnapshot snapshot = PayrollPeriodSnapshot.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.VALIDATED)
                .warnings(List.of())
                .build();
        assertNotNull(snapshot.getId());
        assertEquals(0, snapshot.getWarnings().size());
    }

    @Test
    @DisplayName("Null warnings defaults to empty list")
    void nullWarningsDefaultsToEmpty() {
        PayrollPeriodSnapshot snapshot = PayrollPeriodSnapshot.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.DRAFT)
                .build();
        assertEquals(0, snapshot.getWarnings().size());
    }

    @Test
    @DisplayName("Missing payrollPeriod is rejected")
    void missingPayrollPeriodRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodSnapshot.builder()
                .status(PayrollSummaryStatus.DRAFT)
                .warnings(List.of())
                .build());
    }

    @Test
    @DisplayName("Missing status is rejected")
    void missingStatusRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodSnapshot.builder()
                .payrollPeriod(aPayrollPeriod())
                .warnings(List.of())
                .build());
    }

    @Test
    @DisplayName("withStatus returns a new instance")
    void withStatusTransition() {
        PayrollPeriodSnapshot original = valid();
        PayrollPeriodSnapshot updated = original.withStatus(PayrollSummaryStatus.VALIDATED);

        assertNotSame(original, updated);
        assertEquals(PayrollSummaryStatus.DRAFT, original.getStatus());
        assertEquals(PayrollSummaryStatus.VALIDATED, updated.getStatus());
    }

    @Test
    @DisplayName("withWarnings returns a new instance")
    void withWarningsTransition() {
        PayrollPeriodSnapshot original = valid();
        PayrollPeriodSnapshot updated = original.withWarnings(List.of("a", "b"));

        assertNotSame(original, updated);
        assertEquals(1, original.getWarnings().size());
        assertEquals(2, updated.getWarnings().size());
    }
}