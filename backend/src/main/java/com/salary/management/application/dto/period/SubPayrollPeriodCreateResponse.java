package com.salary.management.application.dto.period;

import lombok.Builder;

import java.util.List;

@Builder
public record SubPayrollPeriodCreateResponse(
        List<PayrollPeriodCreateRequest> newPeriods,
        List<PayrollPeriodResponse> exist
) {}