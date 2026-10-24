package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriod} aggregates. */
public interface PayrollPeriodRepository {

    PayrollPeriod save(PayrollPeriod payrollPeriod);

    Optional<PayrollPeriod> findById(UUID id);

    Optional<PayrollPeriod> findByName(String name);

    List<PayrollPeriod> list(PayrollPeriodCriteria criteria);

    DataPage<PayrollPeriod> page(PayrollPeriodCriteria criteria);

    void deleteById(UUID id);
}