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
 */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  /**
   * Calls `POST /api/v1/reports/aggregate`.
   *
   * On any error, throws `ReportsError`.
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
 *  2. A raw `HttpErrorResponse` (if the interceptor is not registered).
 *  3. Anything else (network failure shaped as `ProgressEvent`, etc.).
 */
function toReportsError(err: unknown): ReportsError {
  if (err instanceof HttpErrorResponse) {
    return fromHttpErrorResponse(err);
  }

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
 *
 * <p>The interceptor's `FieldError.attributes` carries the whole backend
 * {@code details} map for a {@code REPORT_BLOCKED} entry. Lifting it into
 * {@code details} here means the page sees the backend's payload directly,
 * e.g. {@code blocker.details['count']} and {@code blocker.details['employees']}.</p>
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
    details: e.attributes,
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
