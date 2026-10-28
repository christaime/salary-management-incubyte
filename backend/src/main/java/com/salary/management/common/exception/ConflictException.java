package com.salary.management.common.exception;

import java.util.Map;

/**
 * Signals a business-state conflict — 409.
 *
 * <p>Carries a structured {@code code} for the client to switch on
 * ({@code PERIOD_CLOSED}, {@code ASSIGNMENT_NOT_IN_EFFECT}, ...) and an optional
 * {@code details} map for extra context (assignment id, period id, ...).</p>
 */
public class ConflictException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public ConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}