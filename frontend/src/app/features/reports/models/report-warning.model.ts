/**
 * Non-fatal issue emitted alongside a successful aggregate report.
 * Mirrors the backend `ReportWarningDto`.
 *
 * `BLOCKER` warnings won't appear in a successful `AggregateReportResponse`
 * (the request would have failed with `REPORT_BLOCKED` instead), but the type
 * is retained so the results panel can render both severities if the backend
 * ever returns them together.
 */
export interface ReportWarningDto {
  /** Backend warning code, e.g. `NEGATIVE_AMOUNT`, `MISSING_CURRENCY`. */
  code: string;
  severity: 'WARNING' | 'BLOCKER';
  /** Human-readable message, suitable for direct display. */
  message: string;
  /** Optional structured data for tooltips / debugging. */
  details?: Record<string, unknown>;
}
