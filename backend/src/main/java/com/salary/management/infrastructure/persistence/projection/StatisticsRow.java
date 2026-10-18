package com.salary.management.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection for one row of the statistics query: the resolved group-key display values,
 * plus the minimum and maximum reference amounts in the group. Read-only; mapped to
 * {@code AggregateRowProjection} by the adapter.
 */
public interface StatisticsRow {

    String getCountryCode();

    String getCountryName();

    UUID getDepartmentCode();

    String getDepartmentName();

    UUID getRoleCode();

    String getRoleName();

    String getEmploymentType();

    String getCurrency();

    BigDecimal getMinReferenceAmount();

    BigDecimal getMaxReferenceAmount();
}