package com.salary.management.application.exception;

import com.salary.management.domain.model.values.ReportWarning;

import java.util.List;

/**
 * Thrown when a report cannot be produced because at least one snapshot build returned a
 * blocker.
 *
 * <p>Carries the blockers that stopped the report and any warnings collected before the
 * blocker was hit, so the controller can return both to the client.</p>
 */
public class ReportBlockedException extends RuntimeException {

    private final List<ReportWarning> blockers;
    private final List<ReportWarning> warnings;

    /**
     * Creates the exception with blockers and warnings.
     *
     * @param blockers blocker warnings that stopped the report
     * @param warnings warnings collected before the blocker
     */
    public ReportBlockedException(List<ReportWarning> blockers, List<ReportWarning> warnings) {
        super("Report is blocked by " + blockers.size() + " blocker(s)");
        this.blockers = List.copyOf(blockers);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * Returns the blockers that stopped the report.
     *
     * @return blocker warnings
     */
    public List<ReportWarning> getBlockers() {
        return blockers;
    }

    /**
     * Returns the warnings collected before the blocker.
     *
     * @return warnings
     */
    public List<ReportWarning> getWarnings() {
        return warnings;
    }
}