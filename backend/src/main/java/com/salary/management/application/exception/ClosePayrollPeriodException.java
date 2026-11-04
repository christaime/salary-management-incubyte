package com.salary.management.application.exception;

import com.salary.management.common.exception.ConflictException;
import com.salary.management.domain.model.values.ReportWarning;

import java.util.List;
import java.util.Map;

/**
 * Signals that a close request is blocked by snapshot blockers — 409.
 *
 * <p>Distinct from a generic conflict because the UI needs both the blockers
 * (which prevent the close) and the warnings (which the HR Manager would have
 * had to accept) to render the pre-flight dialog correctly.</p>
 */
public class ClosePayrollPeriodException extends ConflictException {

    private final List<ReportWarning> blockers;
    private final List<ReportWarning> warnings;

    public ClosePayrollPeriodException(List<ReportWarning> blockers,
                                       List<ReportWarning> warnings) {
        super(
                "CLOSE_BLOCKED",
                "The pay schedule cannot be settled because of data issues.",
                Map.of(
                        "blockers", blockers == null ? List.of() : blockers,
                        "warnings", warnings == null ? List.of() : warnings));
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public List<ReportWarning> getBlockers() {
        return blockers;
    }

    public List<ReportWarning> getWarnings() {
        return warnings;
    }
}