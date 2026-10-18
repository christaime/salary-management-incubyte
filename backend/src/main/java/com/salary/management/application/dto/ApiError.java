package com.salary.management.application.dto;

import org.springframework.web.ErrorResponse;

import java.util.Map;

/**
 * One error entry in an {@link ErrorResponse}.
 *
 * <p>{@code code} is a stable machine-readable identifier, {@code message} is human-readable,
 * and {@code details} carries structured context such as the offending field or the blocker
 * details.</p>
 *
 * @param code    stable error code
 * @param message human-readable message
 * @param details additional structured details
 */
public record ApiError(String code, String message, Map<String, Object> details) {
}