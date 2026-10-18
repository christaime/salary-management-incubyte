package com.salary.management.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection for one row of a top-N or bottom-N query: the employee, its total, and its
 * rank within the group. No direction column: the direction is known from which query
 * produced the row ({@code topByGroup} → TOP, {@code bottomByGroup} → BOTTOM). Read-only;
 * mapped to {@code RankedSummary} by the adapter.
 */
public interface RankedRow {

    UUID getPayrollPeriodId();

    UUID getCountryId();

    UUID getDepartmentId();

    UUID getRoleId();

    String getEmploymentType();

    Integer getRank();

    UUID getEmployeeId();

    BigDecimal getTotal();
}