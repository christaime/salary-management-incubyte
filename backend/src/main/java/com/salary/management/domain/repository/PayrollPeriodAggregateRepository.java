package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.repository.criteria.PayrollPeriodAggregateCriteria;

import java.util.List;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriodAggregate} rows. */
public interface PayrollPeriodAggregateRepository {

    List<PayrollPeriodAggregate> saveAll(List<PayrollPeriodAggregate> aggregates);

    List<PayrollPeriodAggregate> findByPayrollPeriodId(UUID payrollPeriodId);

    List<PayrollPeriodAggregate> list(PayrollPeriodAggregateCriteria criteria);

    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}