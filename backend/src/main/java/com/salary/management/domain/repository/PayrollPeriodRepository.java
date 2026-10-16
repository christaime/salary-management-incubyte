package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollPeriod} aggregates.
 */
public interface PayrollPeriodRepository {

    PayrollPeriod save(PayrollPeriod payrollPeriod);

    Optional<PayrollPeriod> findById(UUID id);

    Optional<PayrollPeriod> findByName(String name);

    List<PayrollPeriod> findAll();

    void deleteById(UUID id);
}