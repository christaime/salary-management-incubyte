package com.salary.management.application.dto.ranking;

import com.salary.management.application.dto.DateRange;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record EarningRankingReportRequest(
        @NotNull UUID periodId,
        GroupKey groupKey,
        UUID countryId,
        UUID departmentId,
        UUID roleId,
        EmploymentType employmentType
) {}