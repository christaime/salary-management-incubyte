package com.salary.management.application.dto.payroll;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record PeriodicAssignmentDto(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        Boolean hasPayroll
) {}
