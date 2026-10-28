package com.salary.management.infrastructure.persistence.adapter.utils;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

/**
 * Standard message builders for {@link DataIntegrityViolationException}s raised by
 * persistence adapters.
 *
 * <p>Keeps the message shape consistent across adapters so that operators and logs
 * see the same structure regardless of which entity failed. The exceptions remain
 * {@link DataIntegrityViolationException}s — this class only centralizes the
 * message format, not the exception type.</p>
 */
public final class DataIntegrityMessages {

    private DataIntegrityMessages() {
    }

    /**
     * Builds the exception raised when a delete is refused because rows still
     * reference the target.
     *
     * @param entityType human-readable entity type, e.g. {@code "Country"}
     * @param id         identifier of the row that could not be deleted
     * @return the exception, ready to be thrown
     */
    public static DataIntegrityViolationException referenced(
            String entityType, UUID id) {
        return new DataIntegrityViolationException(String.format(
                "%s is referenced and cannot be deleted: id=%s", entityType, id));
    }

    /**
     * Builds the exception raised when a batch operation violates an invariant.
     *
     * @param entityType human-readable entity type, e.g. {@code "CurrencyRate"}
     * @param operation  operation name, e.g. {@code "saveAll"}
     * @param reason     short description of the violated invariant
     * @return the exception, ready to be thrown
     */
    public static DataIntegrityViolationException invariant(
            String entityType, String operation, String reason) {
        return new DataIntegrityViolationException(String.format(
                "%s.%s: %s", entityType, operation, reason));
    }

    /**
     * Builds the exception raised when a single row violates an invariant.
     *
     * @param entityType human-readable entity type
     * @param rowId      identifier of the offending row
     * @param reason     short description of the violated invariant
     * @return the exception, ready to be thrown
     */
    public static DataIntegrityViolationException invalidRow(
            String entityType, UUID rowId, String reason) {
        return new DataIntegrityViolationException(String.format(
                "%s is invalid: id=%s, reason=%s", entityType, rowId, reason));
    }
}