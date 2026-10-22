import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { apiUrl } from '@core/config/api-base-url';
import {
  AggregateReportRequest,
  AggregateReportResponse,
} from '../models';
import { ErrorResponse } from '../models/error-response.model';
import { ReportWarningDto } from '../models/report-warning.model';
import { ReportsError } from './reports-error';

/** Endpoint for the aggregate report. */
const AGGREGATE_REPORT_URL = apiUrl('/api/v1/reports/aggregate');

/**
 * Client for the reports API.
 *
 * Every error path is normalized to `ReportsError` so the page can switch
 * on `code` without dealing with `HttpErrorResponse` or the generic
 * `ApiError` produced by the HTTP error interceptor.
 *
 * Note on double-handling: the app-level `httpErrorInterceptor` maps every
 * failed HTTP response into an `ApiError` and rethrows it. This service
 * re-maps the (already-intercepted) `ApiError` back into `ReportsError`.
 * That's deliberate — the interceptor's job is generic (toast + normalise
 * for generic consumers), the service's job is report-specific (expose
 * `blockers` and the raw `ValidationError[]` for the form).
 */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  /**
   * Calls `POST /api/v1/reports/aggregate`.
   *
   * Sends the request as-is (filters are `null` when no filter is applied —
   * the request model guarantees this). On any error, throws `ReportsError`.
   */
  generateAggregateReport(
    request: AggregateReportRequest,
  ): Observable<AggregateReportResponse> {
    return this.http
      .post<AggregateReportResponse>(AGGREGATE_REPORT_URL, request)
      .pipe(catchError((err: unknown) => throwError(() => toReportsError(err))));
  }
}

/**
 * Converts whatever the interceptor (or `HttpClient`) propagated into a
 * `ReportsError`. Handles three cases:
 *
 *  1. `ApiError` (the interceptor's output) — the common case.
 *  2. A raw `HttpErrorResponse` (e.g. if the interceptor is not registered).
 *  3. Anything else (network failure shaped as `ProgressEvent`, etc.).
 */
function toReportsError(err: unknown): ReportsError {
  // Case 2 & 3: raw HttpErrorResponse / unknown.
  if (err instanceof HttpErrorResponse) {
    return fromHttpErrorResponse(err);
  }

  // Case 1: ApiError from the interceptor.
  if (isApiErrorShape(err)) {
    const code = err.code;
    const fieldErrors = err.fieldErrors;
    const blockers =
      code === 'REPORT_BLOCKED' ? toBlockers(fieldErrors) : [];

    return {
      code,
      message: err.message,
      fieldErrors,
      blockers,
      status: err.status,
    };
  }

  // Unknown — shouldn't happen, but don't crash the UI.
  return {
    code: 'CLIENT_ERROR',
    message: 'An unexpected error occurred.',
    fieldErrors: [],
    blockers: [],
    status: 0,
  };
}

function fromHttpErrorResponse(err: HttpErrorResponse): ReportsError {
  if (err.status === 0) {
    return {
      code: 'NETWORK_ERROR',
      message: 'Network error. Check your connection and try again.',
      fieldErrors: [],
      blockers: [],
      status: 0,
    };
  }

  const body = err.error as ErrorResponse | null;

  if (!body || typeof body.code !== 'string') {
    return {
      code: err.status >= 500 ? 'SERVER_ERROR' : 'HTTP_ERROR',
      message:
        err.status >= 500
          ? 'The server encountered an error. Please try again later.'
          : err.message || 'Request failed.',
      fieldErrors: [],
      blockers: [],
      status: err.status,
    };
  }

  const fieldErrors = body.errors ?? [];
  const blockers =
    body.code === 'REPORT_BLOCKED' ? toBlockers(fieldErrors) : [];

  return {
    code: body.code,
    message: body.message ?? 'Request failed.',
    fieldErrors,
    blockers,
    status: err.status,
  };
}

/**
 * Transforms validation errors into displayable blockers.
 * The `attributes` are folded into `details` so the page can show them in
 * a tooltip without inventing a second field.
 */
function toBlockers(
  fieldErrors: ReadonlyArray<{
    field: string;
    code: string;
    message: string;
    attributes: Record<string, unknown>;
  }>,
): ReportWarningDto[] {
  return fieldErrors.map((e) => ({
    code: e.code,
    severity: 'BLOCKER',
    message: e.message,
    details: { field: e.field, attributes: e.attributes },
  }));
}

/** Runtime check for the interceptor's `ApiError` shape. */
function isApiErrorShape(
  value: unknown,
): value is {
  code: string;
  message: string;
  fieldErrors: ReadonlyArray<{
    field: string;
    code: string;
    message: string;
    attributes: Record<string, unknown>;
  }>;
  status: number;
} {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['code'] === 'string' &&
    typeof v['message'] === 'string' &&
    Array.isArray(v['fieldErrors']) &&
    typeof v['status'] === 'number'
  );
}
