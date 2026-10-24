package com.salary.management.domain.model.values;

import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated view of all VALIDATED payroll summaries sharing a (period, country,
 * department, role, employment type) tuple.
 *
 * <p>Not a persisted entity: it is the shape the snapshot builder consumes before
 * converting amounts and constructing a {@code PayrollPeriodAggregate}. Amounts are in the
 * group's currency ({@code currency}), which is the assignment country's currency.</p>
 */
public record GroupedSummary(
        UUID payrollPeriodId,
        UUID countryId,
        UUID departmentId,
        UUID roleId,
        EmploymentType employmentType,
        String currency,
        int employeeCount,
        BigDecimal base,
        BigDecimal variable,
        BigDecimal allowances,
        BigDecimal employerTaxes,
        BigDecimal total
) {
}