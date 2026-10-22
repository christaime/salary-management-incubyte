import { ValidationError } from '../models/error-response.model';
import { ReportWarningDto } from '../models/report-warning.model';

/**
 * Report-specific error shape produced by `ReportsService`.
 *
 * Unlike the generic `ApiError` from `core/http/api-error.ts`, this carries
 * the raw `ValidationError[]` (needed for inline field errors on the form)
 * and a `blockers` array (populated when the backend rejects the request
 * with `REPORT_BLOCKED`).
 *
 * Note: the backend's `ErrorResponse` does not carry warnings — warnings
 * only appear in successful `AggregateReportResponse`. So this type has no
 * `warnings` field by design.
 */
export interface ReportsError {
  /** Backend error code, e.g. `VALIDATION_FAILED`, `REPORT_BLOCKED`. */
  code: string;
  /** Human-readable message. */
  message: string;
  /** Raw per-field validation errors. Empty array when none. */
  fieldErrors: readonly ValidationError[];
  /**
   * Populated when `code === 'REPORT_BLOCKED'`. Empty array otherwise.
   * Transformed from `fieldErrors` with `severity = 'BLOCKER'`.
   */
  blockers: ReportWarningDto[];
  /** HTTP status, when available. */
  status: number;
}
