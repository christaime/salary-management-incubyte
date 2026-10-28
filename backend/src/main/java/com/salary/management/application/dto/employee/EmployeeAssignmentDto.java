package com.salary.management.application.dto.employee;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Assignment information attached to an employee in a report.
 *
 * <p>{@code unEditable} is only populated by
 * {@link com.salary.management.application.service.EmployeeService#findById}
 * and is {@code null} in list/search responses.</p>
 */
@Builder
public record EmployeeAssignmentDto(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        RefDto country,
        RefDto department,
        RefDto role,
        EmploymentType employmentType,
        PayrollPeriodFrequency payrollFrequency,
        Boolean hasPayroll
) {}