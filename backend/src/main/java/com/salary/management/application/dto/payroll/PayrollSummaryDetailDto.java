package com.salary.management.application.dto.payroll;

import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import lombok.Builder;

import java.util.UUID;

/**
 * Full payload returned by {@code GET /api/v1/payroll-summaries/{id}}.
 *
 * <p>Adds the assignment id and the {@code editable} flag on top of the
 * {@link PayrollSummaryDto} shape. {@code editable} is {@code true} only
 * when the summary is in {@code DRAFT} and may be edited or deleted.</p>
 */
@Builder
public record PayrollSummaryDetailDto(
        UUID id,
        PayrollSummaryStatus status,
        EmployeeRefDto employee,
        PayrollPeriodDto period,
        RefDto country,
        RefDto department,
        RefDto role,
        EmploymentType employmentType,
        AmountsDto amounts,
        PeriodicAssignmentDto assignment
) {}