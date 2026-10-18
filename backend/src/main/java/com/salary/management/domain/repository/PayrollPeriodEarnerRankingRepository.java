package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.values.RankedSummary;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;

import java.util.List;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriodEarnerRanking} rows. */
public interface PayrollPeriodEarnerRankingRepository {

    void saveAll(List<PayrollPeriodEarnerRanking> rankings);

    List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId);

    List<PayrollPeriodEarnerRanking> list(PayrollPeriodEarnerRankingCriteria criteria);

    /**
     * Deletes the DRAFT rankings for a payroll period, leaving VALIDATED rows untouched.
     *
     * <p>Used before persisting a freshly built snapshot for an open period.</p>
     *
     * @param payrollPeriodId payroll period identifier
     */
    void deleteDraftsByPayrollPeriodId(UUID payrollPeriodId);

    /** Top {@code limit} employees by total DESC per group, VALIDATED summaries only. */
    List<RankedSummary> topByGroup(UUID payrollPeriodId, int limit);

    /** Bottom {@code limit} employees by total ASC per group, VALIDATED summaries only. */
    List<RankedSummary> bottomByGroup(UUID payrollPeriodId, int limit);
}