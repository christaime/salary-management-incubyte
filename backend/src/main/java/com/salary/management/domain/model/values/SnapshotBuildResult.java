package com.salary.management.domain.model.values;

import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;

import java.util.List;

/**
 * Result of a snapshot build. Holds the aggregates and rankings the builder computed for a
 * period, plus the warnings and blockers it collected along the way.
 *
 * <p>{@code warnings} are non-blocking data-quality issues (e.g. an employee with an
 * in-effect assignment but no payroll summary for the period). {@code blockers} are issues
 * that make the result untrustworthy (e.g. a missing FX rate for a group's currency, or a
 * non-VALIDATED summary). When {@link #isBlocked()} is true, the caller must not persist the
 * result: the lists may be partial, and any aggregates without a reference side are exactly
 * the groups whose {@code referenceAmounts} are {@code null}.</p>
 *
 * <p>All four lists are defensively copied so the record is immutable. Null inputs are
 * treated as empty.</p>
 */
public record SnapshotBuildResult(
        List<PayrollPeriodAggregate> aggregates,
        List<PayrollPeriodEarnerRanking> rankings,
        List<ReportWarning> warnings,
        List<ReportWarning> blockers
) {

    public SnapshotBuildResult {
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
        rankings = rankings == null ? List.of() : List.copyOf(rankings);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    /** True when at least one blocker was collected. The caller must not persist then. */
    public boolean isBlocked() {
        return !blockers.isEmpty();
    }
}