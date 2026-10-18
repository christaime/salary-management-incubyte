package com.salary.management.infrastructure.persistence.projection;

import com.salary.management.domain.model.values.Amount;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projection for one row of a top-N or bottom-N query. Read-only; mapped to
 * {@code RankedSummary} by the adapter.
 */
public interface RankedRow {

    UUID getPayrollPeriodId();

    UUID getCountryId();

    UUID getDepartmentId();

    UUID getRoleId();

    String getEmploymentType();

    Integer getRank();

    UUID getEmployeeId();

    BigDecimal getOriginalTotal();

    String getOriginalCurrency();

    BigDecimal getReferenceTotal();

    String getReferenceCurrency();

}