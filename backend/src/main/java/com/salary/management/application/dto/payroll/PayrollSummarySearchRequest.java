package com.salary.management.application.dto.payroll;

import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Builder
public record PayrollSummarySearchRequest(
        @NotNull LocalDate rangeStart,
        @NotNull LocalDate rangeEnd,
        UUID employeeId,
        @Size(max = 100) String employeeMatricule,
        Set<UUID> anyCountryIds,
        Set<UUID> anyDepartmentIds,
        Set<UUID> anyRoleIds,
        Set<EmploymentType> anyEmploymentTypes,
        PayrollSummaryStatus status,
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        @Size(max = 50) String sortBy,
        SortDirection sortDirection
) {}