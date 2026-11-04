package com.salary.management.application.dto.period;

import com.salary.management.domain.model.values.ReportWarning;
import lombok.Builder;

import java.util.List;

/**
 * Result of a close pre-flight or a close operation.
 *
 * <p>{@code periods} lists the monthly period followed by its bi-weekly
 * sub-periods, in start-date order. On pre-flight they are not yet closed; on
 * close they are the rows whose {@code closed} flag is now true. {@code warnings}
 * carries the non-blocking data-quality issues the HR Manager accepts by
 * proceeding.</p>
 */
@Builder
public record ClosePeriodResponse(
        List<PayrollPeriodResponse> periods,
        List<ReportWarning> warnings
) {}