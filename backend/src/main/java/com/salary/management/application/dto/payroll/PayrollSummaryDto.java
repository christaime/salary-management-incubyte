package com.salary.management.application.dto.payroll;

import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import lombok.Builder;

import java.util.UUID;

/**
 * One row of the payroll summary list.
 *
 * <p>Carries the summary's id and status, the resolved employee reference,
 * the payroll period (id, name, dates, frequency), the assignment's four
 * dimension references, and the amounts in the assignment's currency.</p>
 */
@Builder
public record PayrollSummaryDto(
        UUID id,
        PayrollSummaryStatus status,
        EmployeeRefDto employee,
        PayrollPeriodDto period,
        RefDto country,
        RefDto department,
        RefDto role,
        PayrollAssignmentDto assignment,
        EmploymentType employmentType,
        AmountsDto amounts
) {}