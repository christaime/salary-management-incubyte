import { ApiError } from '@core/http/api-error';
import { ReportWarning } from '@features/reports/services/reports-error';

/**
 * Normalized error returned by PayrollPeriodsService for every failed call.
 *
 * `blockers` / `warnings` come from `CLOSE_BLOCKED` and are already mapped to
 * the typed `ReportWarning` union. `errors` is the raw `ApiError[]` from the
 * interceptor; it is optional and only needed by callers that must distinguish
 * specific conflict codes (e.g. PERIOD_NAME_CONFLICT vs PERIOD_RANGE_CONFLICT).
 */
export interface PayrollPeriodError {
  code: string;
  message: string;
  blockers: ReportWarning[];
  warnings: ReportWarning[];
  errors?: ApiError[];
  status?: number;
  timestamp?: string;
}
