package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollSummary;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollSummary} aggregates.
 */
public interface PayrollSummaryRepository {

    PayrollSummary save(PayrollSummary payrollSummary);

    Optional<PayrollSummary> findById(UUID id);

    Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId);

    void deleteById(UUID id);
}