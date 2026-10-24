package com.salary.management.domain.repository;

import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
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
     * Returns, per group, the minimum and maximum reference amounts of the ranking
     * population across the given periods.
     *
     * <p>Reads the ranking rows only, and converts each row's original amount to the given
     * reference currency: {@code original * reference_amount / rate}, with identity for the
     * reference currency itself. Rows whose currency has no rate are excluded. The
     * statistics therefore reflect the population that was ranked — the top and bottom of
     * each group — not the full set of payroll summaries.</p>
     *
     * <p>Each returned row carries the requested group-key values, plus
     * {@code minReferenceAmount} and {@code maxReferenceAmount}. Fields not selected as
     * group keys are left {@code null}.</p>
     *
     * @param periodIds             periods to include; must be non-empty
     * @param groupKeys             group keys to group by
     * @param filters               selection filters, with group-key dimensions already
     *                              removed
     * @param referenceCurrencyCode currency to convert the original amounts to
     * @return one row per distinct key combination with {@code min} and {@code max}
     */
    List<AggregateRowProjection> queryStatistics(
            List<UUID> periodIds,
            List<GroupKey> groupKeys,
            PayrollPeriodFrequency frequency,
            ReportEffectiveFilters filters,
            String referenceCurrencyCode);
}