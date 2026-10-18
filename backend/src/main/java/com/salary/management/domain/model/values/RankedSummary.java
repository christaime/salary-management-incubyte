package com.salary.management.domain.model.values;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One entry in a top-N or bottom-N list of VALIDATED payroll summaries inside a group.
 *
 * <p>Not a persisted entity: it is the shape the snapshot builder consumes before loading
 * the employee and constructing a {@code PayrollPeriodEarnerRanking}. {@code total} is in
 * the group's currency; {@code direction} is set by the adapter based on which port method
 * produced the row.</p>
 */
public record RankedSummary(
        UUID payrollPeriodId,
        UUID countryId,
        UUID departmentId,
        UUID roleId,
        EmploymentType employmentType,
        EarnerRankingDirection direction,
        int rank,
        UUID employeeId,
        BigDecimal total
) {
}