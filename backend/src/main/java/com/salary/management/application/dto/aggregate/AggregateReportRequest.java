package com.salary.management.application.dto.aggregate;

import com.salary.management.application.dto.DateRange;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request body for {@code POST /reports/aggregate}.
 *
 * <p>{@code groupKeys} may be empty, in which case the report returns a single total line.
 * When {@code includeStatistics} is {@code true}, {@code groupKeys} must contain
 * {@code COUNTRY}, {@code DEPARTMENT}, and {@code ROLE}; the service enforces that
 * constraint.</p>
 *
 * <p>Selection filters are optional. A filter whose dimension also appears in
 * {@code groupKeys} is ignored by the service: a dimension cannot be both grouped and
 * filtered. Filters are applied to the aggregate and statistics queries before grouping.</p>
 *
 * @param dateRange          inclusive date range to report on
 * @param groupKeys          grouping dimensions; may be empty
 * @param anyCountryIds      optional filter on country ids
 * @param anyDepartmentIds   optional filter on department ids
 * @param anyRoleIds         optional filter on role ids
 * @param anyEmploymentTypes optional filter on employment types
 * @param includeBreakdown   whether to include the amount breakdown per line
 * @param includeStatistics  whether to include min, max, and average per line
 */
public record AggregateReportRequest(
        @NotNull @Valid DateRange dateRange,
        @NotNull List<GroupKey> groupKeys,
        Set<UUID> anyCountryIds,
        Set<UUID> anyDepartmentIds,
        Set<UUID> anyRoleIds,
        Set<EmploymentType> anyEmploymentTypes,
        Boolean includeBreakdown,
        Boolean includeStatistics) {

    /**
     * Returns whether the breakdown should be included, treating {@code null} as
     * {@code false}.
     *
     * @return {@code true} when breakdown is requested
     */
    public boolean breakdownRequested() {
        return Boolean.TRUE.equals(includeBreakdown);
    }

    /**
     * Returns whether statistics should be included, treating {@code null} as
     * {@code false}.
     *
     * @return {@code true} when statistics are requested
     */
    public boolean statisticsRequested() {
        return Boolean.TRUE.equals(includeStatistics);
    }
}