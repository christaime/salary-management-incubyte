package com.salary.management.application.dto.payroll;


import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PayrollSummaryUpdateRequest(
        @NotNull UUID assignmentId,
        @NotNull BigDecimal base,
        @NotNull BigDecimal variable,
        @NotNull BigDecimal allowances,
        @NotNull BigDecimal employerTaxes,
        BigDecimal total,
        @NotNull String currency
) {}
