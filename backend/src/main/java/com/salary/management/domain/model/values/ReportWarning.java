package com.salary.management.domain.model.values;

import com.salary.management.domain.model.enums.ReportWarningCode;

import java.util.Map;

/**
 * A data-quality issue found while producing a report or snapshot.
 *
 * <p>{@code severity} distinguishes a {@link Severity#WARNING} — the report can still be
 * produced, but the consumer should be aware — from a {@link Severity#BLOCKER} — the report
 * is not trustworthy until the issue is fixed. {@code details} carries structured context
 * keyed by the shape documented on {@link ReportWarningCode}; it defaults to an empty map
 * when null.</p>
 */
public record ReportWarning(
        ReportWarningCode code,
        Severity severity,
        String message,
        Map<String, Object> details
) {

    /** Classification of a warning. */
    public enum Severity {
        WARNING,
        BLOCKER
    }

    public ReportWarning {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}