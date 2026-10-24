package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPeriodTest {

    private static PayrollPeriod valid() {
        return PayrollPeriod.builder()
                .name("2024-01 Monthly")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    @Test
    @DisplayName("Valid payroll period is constructed and id is generated")
    void validConstruction() {
        PayrollPeriod period = valid();
        assertNotNull(period.getId());
        assertEquals("2024-01 Monthly", period.getName());
        assertEquals(PayrollPeriodFrequency.MONTHLY, period.getFrequency());
        assertEquals(false, period.getClosed());
    }

    @Test
    @DisplayName("startDate must be before endDate")
    void startDateBeforeEndDate() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriod.builder()
                .name("bad")
                .startDate(LocalDate.of(2024, 2, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build());
    }

    @Test
    @DisplayName("Equal startDate and endDate is rejected")
    void equalDatesRejected() {
        LocalDate same = LocalDate.of(2024, 1, 1);
        assertThrows(DomainValidationException.class, () -> PayrollPeriod.builder()
                .name("bad")
                .startDate(same)
                .endDate(same)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriod.builder()
                .name(" ")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build());
    }

    @Test
    @DisplayName("Missing frequency is rejected")
    void missingFrequencyRejected() {
        assertThrows(DomainValidationException.class, () -> PayrollPeriod.builder()
                .name("2024-01 Monthly")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .closed(false)
                .build());
    }

    @Test
    @DisplayName("withClosed returns a new instance")
    void withClosedTransition() {
        PayrollPeriod original = valid();
        PayrollPeriod updated = original.withClosed(true);

        assertNotSame(original, updated);
        assertEquals(false, original.getClosed());
        assertEquals(true, updated.getClosed());
    }

    @Test
    @DisplayName("withName rejects blank input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName(" "));
    }
}