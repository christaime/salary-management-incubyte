import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';

import { apiUrl } from '@core/config/api-base-url';
import { ApiError } from '@core/http/api-error';
import { DataPage } from '@core/models';
import { mapReportWarning } from '@features/reports/services/reports.service';
import { ReportWarning } from '@features/reports/services/reports-error';
import {
  ClosePeriodResponse,
  PayrollPeriodBatchCreationResult,
  PayrollPeriodCreateRequest,
  PayrollPeriodCriteria,
  PayrollPeriodDto,
  SubPayrollPeriodCreateResponse,
} from '../models';
import { PayrollPeriodError } from './payroll-period-error';

@Injectable({ providedIn: 'root' })
export class PayrollPeriodsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = apiUrl('/api/v1/payroll-periods');

  // ---------- Search ----------
  search(criteria: PayrollPeriodCriteria = {}): Observable<DataPage<PayrollPeriodDto>> {
    return this.http
      .get<DataPage<PayrollPeriodDto>>(this.baseUrl, {
        params: this.toSearchParams(criteria),
      })
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  //---------- Read by id ----------
  getById(id: string): Observable<PayrollPeriodDto> {
    return this.http
      .get<PayrollPeriodDto>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  // ---------- Create ----------
  create(body: PayrollPeriodCreateRequest): Observable<PayrollPeriodDto> {
    return this.http
      .post<PayrollPeriodDto>(this.baseUrl, body)
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  createBatch(
    body: PayrollPeriodCreateRequest[],
  ): Observable<PayrollPeriodBatchCreationResult> {
    return this.http
      .post<PayrollPeriodBatchCreationResult>(`${this.baseUrl}/batch`, body)
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  generateSubPeriods(
    startDate: string,
    endDate: string,
  ): Observable<SubPayrollPeriodCreateResponse> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);

    return this.http
      .get<SubPayrollPeriodCreateResponse>(`${this.baseUrl}/generate-sub`, { params })
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  // ---------- Update ----------
  update(id: string, body: PayrollPeriodCreateRequest): Observable<PayrollPeriodDto> {
    return this.http
      .put<PayrollPeriodDto>(`${this.baseUrl}/${id}`, body)
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  // ---------- Delete ----------
  delete(id: string): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  deleteBatch(ids: string[]): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/batch`, { body: ids })
      .pipe(catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))));
  }

  // ---------- Settle (close) ----------
  checkClose(id: string): Observable<ClosePeriodResponse> {
    return this.http
      .post<ClosePeriodResponse>(`${this.baseUrl}/${id}/check-close`, {})
      .pipe(
        map(normalizeCloseResponse),
        catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))),
      );
  }

  close(id: string): Observable<ClosePeriodResponse> {
    return this.http
      .post<ClosePeriodResponse>(`${this.baseUrl}/${id}/close`, {})
      .pipe(
        map(normalizeCloseResponse),
        catchError((err: unknown) => throwError(() => toPayrollPeriodError(err))),
      );
  }

  // ---------- Helpers ----------
  private toSearchParams(criteria: PayrollPeriodCriteria): HttpParams {
    let params = new HttpParams();

    if (criteria.keyword) params = params.set('keyword', criteria.keyword);
    if (criteria.startDate) params = params.set('startDate', criteria.startDate);
    if (criteria.endDate) params = params.set('endDate', criteria.endDate);
    if (criteria.frequency) params = params.set('frequency', criteria.frequency);
    if (criteria.isClosed !== undefined && criteria.isClosed !== null) {
      params = params.set('isClosed', criteria.isClosed.toString());
    }

    params = params.set('page', (criteria.page ?? 0).toString());
    params = params.set('size', (criteria.size ?? 20).toString());
    params = params.set('sortBy', criteria.sortBy ?? 'startDate');
    params = params.set('sortDirection', criteria.sortDirection ?? 'DESC');

    return params;
  }
}

// ------------------------------------------------------------------
// Response normalization — same pattern as toReportsError
// ------------------------------------------------------------------

/** Map the `warnings` field of a close response through `mapReportWarning`. */
function normalizeCloseResponse(res: ClosePeriodResponse): ClosePeriodResponse {
  return {
    ...res,
    warnings: (res.warnings ?? []).map(mapReportWarning),
  };
}

// ------------------------------------------------------------------
// Error normalization — same pattern as toReportsError
// ------------------------------------------------------------------

/**
 * The HTTP interceptor already normalized the error to `ApiError`
 * (`{ code, message, severity, errors? }`). This function extracts the
 * CLOSE_BLOCKED buckets out of `errors[].details` and maps them to
 * `ReportWarning` — the same mapper the reports service uses.
 */
export function toPayrollPeriodError(err: unknown): PayrollPeriodError {
  const body = (err as ApiError) ?? {};
  const errors = body.errors ?? [];

  const blockers: ReportWarning[] = errors
    .flatMap((e) => (e.details?.['blockers'] as unknown[]) ?? [])
    .map(mapReportWarning);

  const warnings: ReportWarning[] = errors
    .flatMap((e) => (e.details?.['warnings'] as unknown[]) ?? [])
    .map(mapReportWarning);

  return {
    code: body.code ?? 'UNKNOWN_ERROR',
    message: body.message ?? 'An unexpected error occurred.',
    errors,
    blockers,
    warnings,
    status: (body as { status?: number }).status,
    timestamp: (body as { timestamp?: string }).timestamp,
  };
}
