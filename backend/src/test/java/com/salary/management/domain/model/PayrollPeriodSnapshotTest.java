package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPeriodSnapshotTest {

    private static PayrollPeriodSnapshot valid() {
        return PayrollPeriodSnapshot.builder()
                .payrollPeriodId(UUID.randomUUID())
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
    }

    @Test
    @DisplayName("Empty warnings list is allowed")
    void emptyWarningsAllowed() {
        PayrollPeriodSnapshot snapshot = PayrollPeriodSnapshot.builder()
                .payrollPeriodId(UUID.randomUUID())
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
                .payrollPeriodId(UUID.randomUUID())
                .status(PayrollSummaryStatus.DRAFT)
                .build();
        assertEquals(0, snapshot.getWarnings().size());
    }

    @Test
    @DisplayName("Missing payrollPeriodId is rejected")
    void missingPayrollPeriodIdRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodSnapshot.builder()
                .status(PayrollSummaryStatus.DRAFT)
                .warnings(List.of())
                .build());
    }

    @Test
    @DisplayName("Missing status is rejected")
    void missingStatusRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriodSnapshot.builder()
                .payrollPeriodId(UUID.randomUUID())
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