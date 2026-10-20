package com.salary.management.application.dto;

import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record PayrollPeriodResponse(
        String id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        PayrollPeriodFrequency frequency,
        Boolean closed
) {}