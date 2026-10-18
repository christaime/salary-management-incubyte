package com.salary.management.domain.repository;

import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;

import java.util.List;
import java.util.UUID;

/** Persistence port for the data needed to build reports. */
public interface ReportDataRepository {

    /**
     * Sums the reference amounts and employee counts across the given periods, grouped by
     * the requested group keys, with optional selection filters.
     *
     * <p>Each returned row carries the requested group-key values, plus
     * {@code sumBase}, {@code sumVariable}, {@code sumAllowances},
     * {@code sumEmployerTaxes}, {@code sumTotal}, and {@code sumEmployeeCount}.
     * Fields not selected as group keys are left {@code null}.</p>
     *
     * @param periodIds periods to include; must be non-empty
     * @param groupKeys group keys to group by
     * @param filters   selection filters, with group-key dimensions already removed
     * @return one row per distinct key combination, after filtering
     */
    List<AggregateRowProjection> queryAggregates(
            List<UUID> periodIds,
            List<GroupKey> groupKeys,
            ReportEffectiveFilters filters);

    /**
     * Returns, per group, the amounts of the {@code TOP} and {@code BOTTOM} rank-1 ranking
     * entries across the given periods, with optional selection filters.
     *
     * <p>Each returned row carries the requested group-key values, plus
     * {@code direction} ({@code "TOP"} or {@code "BOTTOM"}) and {@code amount}. Rows are
     * grouped by the requested keys and by direction, so a group can produce up to two
     * rows. Fields not selected as group keys are left {@code null}.</p>
     *
     * @param periodIds periods to include; must be non-empty
     * @param groupKeys group keys to group by
     * @param filters   selection filters, with group-key dimensions already removed
     * @return one row per group and direction, after filtering
     */
    List<AggregateRowProjection> queryStatistics(
            List<UUID> periodIds,
            List<GroupKey> groupKeys,
            ReportEffectiveFilters filters);
}