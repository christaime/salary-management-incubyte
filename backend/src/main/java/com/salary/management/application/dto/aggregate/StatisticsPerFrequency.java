package com.salary.management.application.dto.aggregate;

import com.salary.management.application.dto.AmountDto;

/**
 * Statistics for one frequency within an aggregate report line.
 *
 * <p>{@code min} and {@code max} are the extremes of the ranked population of the group for
 * this frequency. {@code total} is the full reference-currency payroll of the group for this
 * frequency, and {@code employeeCount} is the number of payslips it is made of, so
 * {@code average = total / employeeCount} can be verified by the reader. {@code periodCount}
 * is the number of distinct periods of this frequency in the report's date range.</p>
 *
 * <p>{@code min} and {@code max} are {@code null} when the group has no ranked rows for the
 * frequency, and {@code average} is {@code null} when {@code employeeCount} is zero. When the
 * group has no rows of the frequency at all, every field is zero or {@code null} and the
 * block can be read as "no employees paid at this frequency in the range".</p>
 *
 * @param min           smallest ranked payslip of the frequency, or {@code null}
 * @param max           largest ranked payslip of the frequency, or {@code null}
 * @param average       mean payslip of the frequency, or {@code null}
 * @param total         total reference-currency payroll of the frequency
 * @param employeeCount number of payslips of the frequency in the group
 * @param periodCount   number of distinct periods of the frequency in the range
 */
public record StatisticsPerFrequency(
        AmountDto min,
        AmountDto max,
        AmountDto average,
        AmountDto total,
        int employeeCount,
        int periodCount) {
}