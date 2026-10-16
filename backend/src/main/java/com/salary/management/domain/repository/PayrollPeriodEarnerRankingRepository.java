package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodEarnerRanking;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollPeriodEarnerRanking} rows.
 */
public interface PayrollPeriodEarnerRankingRepository {

    List<PayrollPeriodEarnerRanking> saveAll(List<PayrollPeriodEarnerRanking> rankings);

    List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId);

    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}