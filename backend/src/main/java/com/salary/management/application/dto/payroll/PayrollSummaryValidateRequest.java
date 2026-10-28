package com.salary.management.application.dto.payroll;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Builder
public record PayrollSummaryValidateRequest(
        @NotNull UUID periodId,
        Set<UUID> anyCountryIds,
        Set<UUID> anyDepartmentIds,
        Set<UUID> anyRoleIds,
        Set<EmploymentType> anyEmploymentTypes
) {}
