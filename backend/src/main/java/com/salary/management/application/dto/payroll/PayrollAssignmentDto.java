package com.salary.management.application.dto.payroll;

import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record PayrollAssignmentDto(
        String id,
        LocalDate startDate,
        LocalDate endDate,
        PayrollPeriodFrequency frequency
) {}
