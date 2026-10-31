package com.salary.management.infrastructure.persistence.projection;

import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection for one row of the grouped payroll summary query: the five original summed
 * amounts, the five converted sums in the reference currency, the reference currency
 * itself, and the employee count for a single
 * (period, country, department, role, employment type, currency) tuple.
 *
 * <p>Read-only. The {@code referenceX} values are 0 when the row's currency has no rate
 * against the requested reference currency (see the JPQL: the {@code CASE} collapses
 * missing rates to 0). The {@code currency} column is the <em>original</em> currency of
 * the summarized rows; {@code referenceCurrency} is the target currency of the
 * conversion and is the same for every row of a given query.</p>
 */
public interface PayrollAggregateRow {

    UUID getCountryId();

    UUID getDepartmentId();

    UUID getRoleId();

    EmploymentType getEmploymentType();

    /** Original currency of the summarized payroll summaries. */
    String getCurrency();

    Long getEmployeeCount();

    BigDecimal getBase();

    BigDecimal getVariable();

    BigDecimal getAllowances();

    BigDecimal getEmployerTaxes();

    BigDecimal getTotal();

    /**
     * Sum of {@code base} converted to {@link #getReferenceCurrency()}.
     * Zero when {@link #getCurrency()} has no rate against the reference currency.
     */
    BigDecimal getReferenceBase();

    /**
     * Sum of {@code variable} converted to {@link #getReferenceCurrency()}.
     * Zero when {@link #getCurrency()} has no rate against the reference currency.
     */
    BigDecimal getReferenceVariable();

    /**
     * Sum of {@code allowances} converted to {@link #getReferenceCurrency()}.
     * Zero when {@link #getCurrency()} has no rate against the reference currency.
     */
    BigDecimal getReferenceAllowances();

    /**
     * Sum of {@code employerTaxes} converted to {@link #getReferenceCurrency()}.
     * Zero when {@link #getCurrency()} has no rate against the reference currency.
     */
    BigDecimal getReferenceEmployerTaxes();

    /**
     * Sum of {@code total} converted to {@link #getReferenceCurrency()}.
     * Zero when {@link #getCurrency()} has no rate against the reference currency.
     */
    BigDecimal getReferenceTotal();

    /**
     * Target currency of the reference conversion. Same value for every row produced by
     * one call to {@code aggregateByGroup}.
     */
    String getReferenceCurrency();
}