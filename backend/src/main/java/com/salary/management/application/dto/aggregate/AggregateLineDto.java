package com.salary.management.application.dto.aggregate;

import com.salary.management.application.dto.AmountDto;
import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;

/**
 * One line of an aggregate report.
 *
 * <p>Only the group-key fields requested by the client are populated. {@code breakdown} is
 * present when {@code includeBreakdown} was set on the request; {@code statistics} is
 * present when {@code includeStatistics} was set.</p>
 *
 * @param amount         total amount in the reference currency
 * @param employeeCount  number of employees in the group
 * @param country        country group key, when requested
 * @param department     department group key, when requested
 * @param role           role group key, when requested
 * @param employmentType employment type group key, when requested
 * @param currency       currency group key, when requested
 * @param breakdown      amount breakdown, when requested
 * @param statistics     min, max, and average, when requested
 */
public record AggregateLineDto(
        AmountDto amount,
        Integer employeeCount,
        RefDto country,
        RefDto department,
        RefDto role,
        EmploymentType employmentType,
        String currency,
        AmountsDto breakdown,
        StatisticsDto statistics) {
}