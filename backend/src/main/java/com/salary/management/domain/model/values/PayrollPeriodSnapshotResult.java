package com.salary.management.domain.model.values;

import java.util.List;

/**
 *   Result of a snapshot build. Holds the warnings and blockers it collected along the way.
 *
 * @param warnings
 * @param blockers
 */
public record PayrollPeriodSnapshotResult(
        List<ReportWarning> warnings,
        List<ReportWarning> blockers
) {
    public PayrollPeriodSnapshotResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
    public static PayrollPeriodSnapshotResult empty(){
        return new PayrollPeriodSnapshotResult(null,null);
    }
    /** True when at least one blocker was collected. The caller must not persist then. */
    public boolean isBlocked() {
        return !blockers.isEmpty();
    }

}
