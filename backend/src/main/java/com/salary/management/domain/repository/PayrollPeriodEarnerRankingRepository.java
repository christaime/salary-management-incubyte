package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.values.RankingAndPercentileSnapshot;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;
import com.salary.management.infrastructure.persistence.repository.DynamicRankingBuilder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriodEarnerRanking} rows. */
public interface PayrollPeriodEarnerRankingRepository {

    void saveAll(List<PayrollPeriodEarnerRanking> rankings);
    void saveAll(List<PayrollPeriodEarnerRanking> rankings, List<PayrollPeriodEarnerPercentile> percentiles);

    List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId);
    List<PayrollPeriodEarnerPercentile> findPercentileByPayrollPeriodId(UUID payrollPeriodId);

    List<PayrollPeriodEarnerRanking> list(PayrollPeriodEarnerRankingCriteria criteria);

    /**
     * Deletes the DRAFT rankings for a payroll period, leaving VALIDATED rows untouched.
     *
     * <p>Used before persisting a freshly built snapshot for an open period.</p>
     *
     * @param payrollPeriodId payroll period identifier
     */
    void deleteDraftsByPayrollPeriodId(UUID payrollPeriodId);

    void deleteDraftsPercentileByPayrollPeriodId(UUID payrollPeriodId);

    /**
     * Ranks the VALIDATED summaries of a period, partitioned by one dimension (or globally
     * when {@code key} is {@code null}), and returns the top or bottom {@code limit}
     * employees of each partition.
     *
     * @param payrollPeriodId       period to rank
     * @param referenceCurrencyCode currency to convert to for the ordering
     * @param key                   partition dimension, or {@code null} for global
     * @param limit                 per-partition row cap
     * @return one row per ranked employee, with its rank within the partition
     */
    public RankingAndPercentileSnapshot rankAndPercentile(
            UUID payrollPeriodId,
            String referenceCurrencyCode,
            GroupKey key,
            int limit);

}