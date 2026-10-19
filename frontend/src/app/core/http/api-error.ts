/**
 * User-facing error shape produced by `httpErrorInterceptor`.
 *
 * Mirrors the backend `ErrorResponse` where possible, but flattens the
 * `errors[]` array into `fieldErrors` keyed by field name so form components
 * can bind to it directly.
 */
export interface FieldError {
  field: string;
  code: string;
  message: string;
  attributes: Record<string, unknown>;
}

export interface ApiError {
  /** Backend error code (e.g. `VALIDATION_FAILED`, `REPORT_BLOCKED`). */
  code: string;
  /** Human-readable message suitable for a toast. */
  message: string;
  /** Per-field validation errors, if any. */
  fieldErrors: FieldError[];
  /** HTTP status, when available. */
  status: number;
}

/** Type guard used by callers that receive an `unknown` error. */
export function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['code'] === 'string' &&
    typeof v['message'] === 'string' &&
    Array.isArray(v['fieldErrors']) &&
    typeof v['status'] === 'number'
  );
}
