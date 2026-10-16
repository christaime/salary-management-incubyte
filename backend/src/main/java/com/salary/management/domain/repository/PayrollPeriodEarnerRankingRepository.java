package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;

import java.util.List;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriodEarnerRanking} rows. */
public interface PayrollPeriodEarnerRankingRepository {

    void saveAll(List<PayrollPeriodEarnerRanking> rankings);

    List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId);

    List<PayrollPeriodEarnerRanking> list(PayrollPeriodEarnerRankingCriteria criteria);

    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}