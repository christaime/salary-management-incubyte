package com.salary.management.infrastructure.api.advice;

import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.values.ReportWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates exceptions raised by controllers into a consistent {@link ErrorResponse}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BLOCKER_SEVERITY = "ERROR";
    private static final String WARNING_SEVERITY = "WARNING";
    /**
     * Handles domain validation failures.
     *
     * @param ex the exception
     * @return 400 with per-field errors
     */
    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ErrorResponse> handleDomainValidation(DomainValidationException ex) {
        List<ApiError> errors = new ArrayList<>();
        for (ValidationError ve : ex.getErrors()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", ve.field());
            details.put("attributes", ve.attributes() == null ? Map.of() : ve.attributes());
            errors.add(new ApiError(ve.code(), ve.message(), BLOCKER_SEVERITY, details));
        }
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.VALIDATION_FAILED,
                "One or more fields are invalid",
                errors,
                Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles a report blocked by snapshot blockers.
     *
     * @param ex the exception
     * @return 400 with blockers and warnings, one {@link ApiError} each
     */
    @ExceptionHandler(ReportBlockedException.class)
    public ResponseEntity<ErrorResponse> handleReportBlocked(ReportBlockedException ex) {
        List<ApiError> errors = new ArrayList<>();
        for (ReportWarning w : ex.getBlockers()) {
            errors.add(toApiError(w, BLOCKER_SEVERITY));
        }
        for (ReportWarning w : ex.getWarnings()) {
            errors.add(toApiError(w, WARNING_SEVERITY));
        }
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.REPORT_BLOCKED,
                "Report could not be generated",
                errors,
                Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles missing resources.
     *
     * @param ex the exception
     * @return 404 with a single error
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ApiError error = new ApiError(
                ErrorResponse.NOT_FOUND, ex.getMessage(), BLOCKER_SEVERITY, Map.of());
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.NOT_FOUND, ex.getMessage(), List.of(error), Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles Jakarta Bean Validation failures on the request body.
     *
     * @param ex the exception
     * @return 400 with per-field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<ApiError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", fe.getField());
            if (fe.getRejectedValue() != null) {
                details.put("rejectedValue", fe.getRejectedValue());
            }
            String code = fe.getCode() == null ? ErrorResponse.INVALID_REQUEST : fe.getCode();
            String message = fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage();
            errors.add(new ApiError(code, message,BLOCKER_SEVERITY, details));
        });
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.INVALID_REQUEST,
                "Request validation failed",
                errors,
                Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles illegal arguments raised by service code.
     *
     * @param ex the exception
     * @return 400 with a single error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError error = new ApiError(
                ErrorResponse.INVALID_REQUEST, ex.getMessage(), BLOCKER_SEVERITY, Map.of());
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.INVALID_REQUEST, ex.getMessage(), List.of(error), Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles malformed or unparseable request bodies.
     *
     * @param ex the exception
     * @return 400 with a single error
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = "Malformed request body";
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            message = "Invalid value for '" + ife.getPath().get(0).getFieldName()
                    + "': " + ife.getValue();
        }
        ApiError error = new ApiError(ErrorResponse.INVALID_REQUEST, message, BLOCKER_SEVERITY, Map.of());
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.INVALID_REQUEST, message, List.of(error), Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler.
     *
     * @param ex the exception
     * @return 500 with a generic error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError error = new ApiError(
                ErrorResponse.INTERNAL_ERROR, "An unexpected error occurred",BLOCKER_SEVERITY, Map.of());
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.INTERNAL_ERROR,
                "An unexpected error occurred",
                List.of(error),
                Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Handles database constraint violations (unique, foreign key, not null,
     * check).
     *
     * <p>Most of these are caller errors — duplicate {@code matricule},
     * deleting a row that is still referenced — and belong to {@code 409 Conflict}.
     * NOT NULL and CHECK violations usually indicate a bug, but they are still
     * a request-level error; mapping to 409 keeps the classification
     * consistent, and the message names the constraint.</p>
     *
     * @param ex the exception
     * @return 409 with a single error naming the violated constraint
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String constraint = extractConstraintName(ex);
        String message = constraint == null
                ? "Request conflicts with existing data"
                : "Request conflicts with existing data (" + constraint + ")";

        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        Map<String, Object> details = new LinkedHashMap<>();
        if (constraint != null) {
            details.put("constraint", constraint);
        }
        ApiError error = new ApiError(
                ErrorResponse.DATA_CONFLICT, message, BLOCKER_SEVERITY, details);
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.DATA_CONFLICT, message, List.of(error), Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handles business-state conflicts raised by the service layer.
     *
     * @param ex the exception
     * @return 409 with the structured code and details
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            details.putAll(ex.getDetails());
        }
        ApiError error = new ApiError(
                ex.getCode(), ex.getMessage(), BLOCKER_SEVERITY, details);
        ErrorResponse body = new ErrorResponse(
                ErrorResponse.DATA_CONFLICT, ex.getMessage(), List.of(error), Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Extracts the violated constraint name from the driver message, if present.
     * SQLite reports "UNIQUE constraint failed: table.column" style; Postgres
     * puts it in a structured field. This is best-effort — {@code null} when
     * nothing can be parsed.
     */
    private String extractConstraintName(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message == null) {
            return null;
        }
        // SQLite: "UNIQUE constraint failed: payroll_summaries.employee_id, payroll_summaries.payroll_period_id"
        int idx = message.indexOf("constraint failed:");
        if (idx >= 0) {
            return message.substring(idx + "constraint failed:".length()).trim();
        }
        // SQLite: "FOREIGN KEY constraint failed"
        if (message.contains("FOREIGN KEY")) {
            return "foreign key";
        }
        return null;
    }

    private ApiError toApiError(ReportWarning w, String severity) {
        return new ApiError(
                w.code().name(),
                w.message(),
                severity,
                w.details() == null ? Map.of() : w.details());
    }
}