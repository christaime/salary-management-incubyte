package com.salary.management.application.dto.employee;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Write payload for an employee's assignment.
 *
 * <p>References are ids only — the client already knows the id from the
 * reference-data lookup. Reads return the denormalized {@link EmployeeAssignmentDto}
 * (with {@code RefDto}); writes accept only ids.</p>
 */
@Builder
public record EmployeeAssignmentSaveRequest(
        UUID id,
        @NotNull UUID countryId,
        @NotNull UUID departmentId,
        @NotNull UUID roleId,
        @NotNull EmploymentType employmentType,
        @NotNull PayrollPeriodFrequency payrollFrequency,
        @NotNull LocalDate startDate,
        LocalDate endDate                  // nullable for open-ended assignment
) {}