package com.salary.management.application.dto.employee;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Assignment information attached to an employee in a report.
 *
 * <p>Carries the fields that describe the assignment's scope and schedule
 * (country, department, role, employment type, payroll frequency, active
 * dates). Does not carry {@code id} (internal to the domain) or
 * {@code inEffect} (always true for the assignments the report selects).</p>
 */
@Builder
public record EmployeeAssignmentDto(
        LocalDate startDate,
        LocalDate endDate,                // nullable for open-ended assignments
        RefDto country,
        RefDto department,
        RefDto role,
        EmploymentType employmentType,
        PayrollPeriodFrequency payrollFrequency
) {}