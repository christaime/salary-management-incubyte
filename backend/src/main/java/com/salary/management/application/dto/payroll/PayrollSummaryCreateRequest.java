package com.salary.management.application.dto.payroll;

import com.salary.management.application.dto.AmountsDto;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PayrollSummaryCreateRequest(
        @NotNull UUID employeeId,
        @NotNull UUID periodId,
        @NotNull UUID assignmentId,
        @NotNull BigDecimal base,
        @NotNull BigDecimal variable,
        @NotNull BigDecimal allowances,
        @NotNull BigDecimal employerTaxes,
        BigDecimal total,
        @NotNull String currency
) {}
