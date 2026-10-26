package com.salary.management.domain.model.enums;

import com.salary.management.domain.model.values.ReportWarning;

/**
 * Known codes for a {@link ReportWarning}. The code identifies the kind of issue; the
 * severity is carried separately on the warning itself.
 *
 * <p>Each constant documents the shape of its {@code details} map so producers and
 * consumers agree on the keys without a runtime discovery step.</p>
 */
public enum ReportWarningCode {

    /**
     * We do not create snapshot on non monthly period
     */
    NOT_MONTHLY_PERIOD,
    /**
     * A closed payroll period was passed to the snapshot builder. Severity: BLOCKER.
     * Details: {@code { "periodId": UUID }}.
     */
    PERIOD_CLOSED,

    /**
     * One or more employees with an in-effect assignment covering the period have no summary.
     * Severity: WARNING. Details:
     * {@code { "count": Integer, "employees": List<EmployeeRefDto> }}, list capped at 10.
     */
    MISSING_PAYROLL_SUMMARY,

    /**
     * One or more employees with an in-effect assignment covering the period have a
     * non-VALIDATED summary. Severity: BLOCKER. Details:
     * {@code { "count": Integer, "employees": List<EmployeeRefDto> }}, list capped at 10.
     */
    UNVALIDATED_PAYROLL_SUMMARY,

    /**
     * A group's currency has no exchange rate to the reference currency. Severity: BLOCKER.
     * Details: {@code { "currency": String }}.
     */
    MISSING_FX_RATE,

    /**
     * The reference currency is not defined. Severity: BLOCKER.
     * Details: {@code { "currency": String }}.
     */
    MISSING_REFERENCE_CURRENCY
}