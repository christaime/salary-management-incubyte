/**
 * Backend validation error entry, as it appears inside `ErrorResponse.errors`.
 * Mirrors the backend `ValidationError` DTO.
 */
export interface ValidationError {
  /** Field name the error refers to, e.g. `dateRange.startDate`. */
  field: string;
  /** Backend error code, e.g. `NOT_NULL`, `INVALID_RANGE`. */
  code: string;
  /** Human-readable message. */
  message: string;
  /** Optional structured attributes (e.g. `{ rejectedValue: "..." }`). */
  attributes: Record<string, unknown>;
}

/**
 * Error response body for any failed API call.
 * Mirrors the backend `ErrorResponse` DTO.
 *
 * The HTTP error interceptor maps this into the shared `ApiError` shape
 * (`core/http/api-error.ts`) before rethrowing, so most UI code should
 * consume `ApiError`, not this.
 */
export interface ErrorResponse {
  /** Backend error code, e.g. `VALIDATION_FAILED`, `REPORT_BLOCKED`. */
  code: string;
  /** Human-readable message. */
  message: string;
  /** Per-field validation errors, if any. `null` when none apply. */
  errors: ValidationError[] | null;
  /** ISO-8601 timestamp of the error, for logging. */
  timestamp: string;
}
