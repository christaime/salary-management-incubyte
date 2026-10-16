package com.salary.management.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.ConstraintDescriptor;

import java.util.*;
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
        if (violations.isEmpty()) {
            return;
        }

        List<ValidationError> errors = violations.stream()
                .map(DomainValidation::toValidationError)
                .sorted(Comparator.comparing(ValidationError::field))
                .toList();

        throw new DomainValidationException(errors);
    }

    /**
     * Converts a Jakarta Bean Validation {@link ConstraintViolation} into the
     * application's structured {@link ValidationError}.
     *
     * <p>The violation's property path becomes the error's {@code field}, the
     * constraint annotation's simple name becomes its {@code code} (for example
     * {@code NotBlank}, {@code Size}, {@code NotNull}), and the violation's
     * message becomes its {@code message}. The constraint's attributes are
     * carried through so the frontend can interpolate values such as
     * {@code min} and {@code max}.
     *
     * <p>Framework-internal attributes ({@code groups}, {@code payload}, and
     * {@code message}) are excluded — they are not useful to API consumers and
     * would only add noise to the error payload.
     *
     * @param violation the constraint violation produced by the validator
     * @param <T>       the type of the object that was validated
     * @return a structured validation error ready to be serialized to the client
     */
    public static <T> ValidationError toValidationError(ConstraintViolation<T> violation) {
        ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor();

        String field = violation.getPropertyPath().toString();
        String code = descriptor.getAnnotation().annotationType().getSimpleName();
        String message = violation.getMessage();

        // Keep only serializable, useful attributes (skip groups, payload, message)
        Map<String, Object> attributes = descriptor.getAttributes().entrySet().stream()
                .filter(e -> !Set.of("groups", "payload", "message").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new ValidationError(field, code, message, attributes);
    }

    /**
     * Validates the object with Bean Validation, merges any extra errors supplied
     * by the caller (cross-field rules), and throws {@link DomainValidationException}
     * if the combined list is not empty. Errors are sorted by field before throwing.
     */
    public static <T> void validate(T object, List<ValidationError> extraErrors) {
        List<ValidationError> errors = new ArrayList<>(extraErrors);

        VALIDATOR.validate(object).stream()
                .map(DomainValidation::toValidationError)
                .forEach(errors::add);

        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(ValidationError::field));
            throw new DomainValidationException(errors);
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