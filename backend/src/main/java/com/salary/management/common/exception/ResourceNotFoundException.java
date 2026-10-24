package com.salary.management.common.exception;

/**
 * Thrown when a requested resource cannot be found.
 *
 * <p>Two constructors are provided: one taking a pre-formatted message, one taking a
 * resource name and identifier, which formats the message as {@code "<resource> not found:
 * <id>"}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a pre-formatted message.
     *
     * @param message human-readable message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates the exception for a resource and identifier.
     *
     * @param resource resource name, e.g. {@code "PayrollPeriod"}
     * @param id       identifier of the missing resource
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}