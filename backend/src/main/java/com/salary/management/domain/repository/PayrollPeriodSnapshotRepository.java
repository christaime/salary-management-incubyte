package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollPeriodSnapshot} aggregates.
 */
public interface PayrollPeriodSnapshotRepository {

    PayrollPeriodSnapshot save(PayrollPeriodSnapshot snapshot);

    Optional<PayrollPeriodSnapshot> findByPayrollPeriodId(UUID payrollPeriodId);

    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}