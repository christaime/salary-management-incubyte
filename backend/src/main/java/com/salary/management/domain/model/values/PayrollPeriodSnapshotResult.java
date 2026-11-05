package com.salary.management.domain.model.values;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 *   Result of a snapshot build. Holds the warnings and blockers it collected along the way.
 *
 * @param warnings
 * @param blockers
 */
@Slf4j
public record PayrollPeriodSnapshotResult(
        List<ReportWarning> warnings,
        List<ReportWarning> blockers
) {
    public PayrollPeriodSnapshotResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
    public static PayrollPeriodSnapshotResult empty(){
        return new PayrollPeriodSnapshotResult(List.of(),List.of());
    }
    /** True when at least one blocker was collected. The caller must not persist then. */
    public boolean hasBlockers() {
        return !blockers.isEmpty();
    }

    public PayrollPeriodSnapshotResult merge(PayrollPeriodSnapshotResult result){
        if (result == null) {
            log.debug("PayrollPeriodSnapshotResult.merge: incoming=null, keeping current ({} warnings, {} blockers)",
                    this.warnings.size(), this.blockers.size());
            return this;
        }
        log.debug("PayrollPeriodSnapshotResult.merge: incoming ({} warnings, {} blockers)",
                result.warnings.size(), result.blockers.size());
        List<ReportWarning> mergeWarnings = new ArrayList<>(this.warnings);
        List<ReportWarning> mergeBlockers = new ArrayList<>(this.blockers);
        mergeWarnings.addAll(result.warnings);
        mergeBlockers.addAll(result.blockers);
        return new PayrollPeriodSnapshotResult(mergeWarnings,mergeBlockers);
    }
}
