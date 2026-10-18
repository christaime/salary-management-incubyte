package com.salary.management.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Inclusive date range used by the aggregate report request and response.
 *
 * <p>Both bounds are required. Start must not be after end; that check lives in the
 * service.</p>
 *
 * @param startDate inclusive start date
 * @param endDate   inclusive end date
 */
public record DateRange(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}