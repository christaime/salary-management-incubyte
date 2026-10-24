package com.salary.management.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection for one row of the grouped payroll summary query: the five summed amounts and
 * the employee count for a single (period, country, department, role, employment type)
 * tuple. Read-only; mapped to {@code GroupedSummary} by the adapter.
 */
public interface AggregateRow {

    UUID getPayrollPeriodId();

    UUID getCountryId();

    UUID getDepartmentId();

    UUID getRoleId();

    String getEmploymentType();

    String getCurrency();

    Long getEmployeeCount();

    BigDecimal getBase();

    BigDecimal getVariable();

    BigDecimal getAllowances();

    BigDecimal getEmployerTaxes();

    BigDecimal getTotal();
}