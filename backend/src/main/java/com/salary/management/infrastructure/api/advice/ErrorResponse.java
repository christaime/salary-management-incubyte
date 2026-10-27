package com.salary.management.infrastructure.api.advice;

import java.time.Instant;
import java.util.List;

/**
 * Error envelope returned by every failing endpoint.
 *
 * @param code      top-level error code
 * @param message   human-readable summary
 * @param errors    individual error entries
 * @param timestamp moment the error was produced
 */
public record ErrorResponse(
        String code,
        String message,
        List<ApiError> errors,
        Instant timestamp) {

    /** One or more domain fields are invalid. Thrown by {@code DomainValidationException}. */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** A snapshot build returned a blocker; the report was not produced. */
    public static final String REPORT_BLOCKED = "REPORT_BLOCKED";

    /** The requested resource does not exist. */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** The HTTP request body failed Jakarta Bean Validation. */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** Any other unexpected failure. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String DATA_CONFLICT = "DATA_CONFLICT";
}