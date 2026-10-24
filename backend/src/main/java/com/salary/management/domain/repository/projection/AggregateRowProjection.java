package com.salary.management.domain.repository.projection;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mutable projection for one row of the report queries.
 *
 * <p>For the aggregate query, the {@code sum*} fields, the frequency totals, and the four
 * frequency counters are populated, and the group-key fields ({@code country},
 * {@code department}, {@code role}, {@code employmentType}, {@code currency}) are filled only
 * for the keys the client asked for. For the statistics query, the group-key fields are
 * populated the same way and {@code minReferenceAmount} and {@code maxReferenceAmount} carry
 * the per-group minimum and maximum of the requested frequency.</p>
 */
public class AggregateRowProjection {

    /** COUNTRY group key value. */
    public RefDto country;

    /** DEPARTMENT group key value. */
    public RefDto department;

    /** ROLE group key value. */
    public RefDto role;

    /** EMPLOYMENT_TYPE group key value. */
    public EmploymentType employmentType;

    /** CURRENCY group key value. */
    public String currency;

    /** Aggregate query totals across all frequencies. */
    public BigDecimal sumBase;
    public BigDecimal sumVariable;
    public BigDecimal sumAllowances;
    public BigDecimal sumEmployerTaxes;
    public BigDecimal sumTotal;

    /**
     * Raw sum of employee counts across all periods in the group. Kept for diagnostics; the
     * value the response uses is {@code employeeCount}.
     */
    public Long sumEmployeeCount;

    /** Total reference amount over MONTHLY aggregate rows. */
    public BigDecimal monthlyTotal;
    /** Sum of {@code employeeCount} over MONTHLY aggregate rows, i.e. monthly payslips. */
    public Long monthlyEmployeeCount;
    /** Number of distinct MONTHLY periods in the group. */
    public Long monthlyPeriodCount;

    /** Total reference amount over BI_WEEKLY aggregate rows. */
    public BigDecimal biWeeklyTotal;
    /** Sum of {@code employeeCount} over BI_WEEKLY aggregate rows, i.e. bi-weekly payslips. */
    public Long biWeeklyEmployeeCount;
    /** Number of distinct BI_WEEKLY periods in the group. */
    public Long biWeeklyPeriodCount;

    /**
     * Average employees paid per period, summed over period types. Computed in the adapter
     * from the four counters:
     * {@code round(monthlyEmployeeCount / monthlyPeriodCount)
     *     + round(biWeeklyEmployeeCount / biWeeklyPeriodCount)}.
     */
    public Long employeeCount;

    /** Statistics query results for the requested frequency. */
    public BigDecimal minReferenceAmount;
    public BigDecimal maxReferenceAmount;

    @Override
    public String toString() {
        return "AggregateRowProjection{" +
                "country=" + country +
                ", department=" + department +
                ", role=" + role +
                ", employmentType=" + employmentType +
                ", currency='" + currency + '\'' +
                ", sumBase=" + sumBase +
                ", sumVariable=" + sumVariable +
                ", sumAllowances=" + sumAllowances +
                ", sumEmployerTaxes=" + sumEmployerTaxes +
                ", sumTotal=" + sumTotal +
                ", sumEmployeeCount=" + sumEmployeeCount +
                ", monthlyTotal=" + monthlyTotal +
                ", monthlyEmployeeCount=" + monthlyEmployeeCount +
                ", monthlyPeriodCount=" + monthlyPeriodCount +
                ", biWeeklyTotal=" + biWeeklyTotal +
                ", biWeeklyEmployeeCount=" + biWeeklyEmployeeCount +
                ", biWeeklyPeriodCount=" + biWeeklyPeriodCount +
                ", employeeCount=" + employeeCount +
                ", minReferenceAmount=" + minReferenceAmount +
                ", maxReferenceAmount=" + maxReferenceAmount +
                '}';
    }
}