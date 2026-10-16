package com.salary.management.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central entry point for running Jakarta Bean Validation against domain objects.
 *
 * <p>Domain builders call {@link #validate(Object)} at the end of {@code build()}
 * so that no invalid instance can ever be constructed. Violations are surfaced as
 * {@link IllegalArgumentException} with a human-readable, joined message.</p>
 *
 * <p>This class lives in the domain layer and depends only on the Bean Validation
 * API — no Spring, no JPA, no HTTP.</p>
 */
public final class DomainValidation {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private DomainValidation() {
        // utility class
    }

    /**
     * Validates the given object and throws if any constraint is violated.
     *
     * @param object the object to validate
     * @param <T>    the object type
     * @throws IllegalArgumentException if any constraint violation is found
     */
    public static <T> void validate(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Validation failed: " + message);
        }
    }

    /**
     * @param object the object to validate
     * @param <T>    the object type
     * @return {@code true} if no constraint is violated
     */
    public static <T> boolean isValid(T object) {
        return VALIDATOR.validate(object).isEmpty();
    }

    /**
     * @param object the object to validate
     * @param <T>    the object type
     * @return the (possibly empty) set of constraint violations
     */
    public static <T> Set<ConstraintViolation<T>> getViolations(T object) {
        return VALIDATOR.validate(object);
    }
}