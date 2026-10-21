package com.salary.management.domain.repository.projection;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mutable projection for one row of the report queries.
 *
 * <p>For the aggregate query, the {@code sum*} fields, {@code sumEmployeeCount}, and the
 * four frequency counters are populated, and the group-key fields
 * ({@code country}, {@code department}, {@code role}, {@code employmentType},
 * {@code currency}) are filled only for the keys the client asked for. For the statistics
 * query, the group-key fields are populated the same way and {@code minReferenceAmount} and
 * {@code maxReferenceAmount} carry the per-group minimum and maximum.</p>
 */
public class AggregateRowProjection {

    /** COUNTRY group key value: code and name. */
    public RefDto country;

    /** DEPARTMENT group key value: code and name. */
    public RefDto department;

    /** ROLE group key value: code and name. */
    public RefDto role;

    /** EMPLOYMENT_TYPE group key value. */
    public EmploymentType employmentType;

    /** CURRENCY group key value. */
    public String currency;

    /** Aggregate query totals. */
    public BigDecimal sumBase;
    public BigDecimal sumVariable;
    public BigDecimal sumAllowances;
    public BigDecimal sumEmployerTaxes;
    public BigDecimal sumTotal;

    /**
     * Raw sum of employee counts across all periods in the group. Kept for diagnostics; the
     * value the response uses is {@code employeeCount}, computed from the four frequency
     * counters below.
     */
    public Long sumEmployeeCount;

    /** Numerator: sum of {@code employeeCount} over MONTHLY aggregate rows. */
    public Long monthlyEmployeeCount;
    /** Denominator: number of MONTHLY aggregate rows. */
    public Long monthlyPeriodCount;
    /** Numerator: sum of {@code employeeCount} over BI_WEEKLY aggregate rows. */
    public Long biWeeklyEmployeeCount;
    /** Denominator: number of BI_WEEKLY aggregate rows. */
    public Long biWeeklyPeriodCount;

    /**
     * Average employees paid per period, summed over period types. Computed in the adapter
     * from the four counters:
     * {@code round(monthlyEmployeeCount / monthlyPeriodCount)
     *     + round(biWeeklyEmployeeCount / biWeeklyPeriodCount)}.
     */
    public Long employeeCount;

    /** Statistics query results. */
    public BigDecimal minReferenceAmount;
    public BigDecimal maxReferenceAmount;
}