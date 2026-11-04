package com.salary.management.application.dto.period;

import com.salary.management.infrastructure.api.advice.ApiError;
import lombok.Builder;

import java.util.List;

/**
 * Outcome of a batch create. Items are independent: some may succeed and some
 * may fail in the same call.
 */
@Builder
public record PayrollPeriodBatchCreationResult(
        List<PayrollPeriodResponse> succeeded,
        List<FailedCreation> failed
) {
    /** One failed item, with the request echoed back and the errors that rejected it. */
    @Builder
    public record FailedCreation(
            PayrollPeriodCreateRequest period,
            List<ApiError> errors
    ) {}
}