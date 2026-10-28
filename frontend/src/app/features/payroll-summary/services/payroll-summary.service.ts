import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { apiUrl } from '@core/config/api-base-url';
import { ApiError } from '@core/http/api-error';
import { FormEditorError } from '@core/models/form-editor-error.model';
import { DataPage } from '@core/models';

import {
  PayrollSummaryDetailDto,
  PayrollSummaryDto,
} from '../models/payroll-summary.model';
import {
  PayrollSummaryCreateRequest,
  PayrollSummaryUpdateRequest,
  PayrollSummaryValidateRequest,
  ValidateAllResult,
} from '../models/payroll-summary-request.model';
import { PayrollSummaryCriteria } from '../models/payroll-summary-criteria.model';
import { toPayrollEditorError } from '../models/payroll-summary-error.mapper';

const BASE_URL = apiUrl('/api/v1/payroll-summaries');

@Injectable({ providedIn: 'root' })
export class PayrollSummaryService {
  private readonly http = inject(HttpClient);

  /** Search the payroll summary list with pagination and filters. */
  search(criteria: PayrollSummaryCriteria): Observable<DataPage<PayrollSummaryDto>> {
    return this.http.get<DataPage<PayrollSummaryDto>>(BASE_URL, {
      params: toHttpParams(criteria),
    });
  }

  /** GET one summary with the assignment details (for the detail page). */
  getById(id: string): Observable<PayrollSummaryDetailDto> {
    return this.http.get<PayrollSummaryDetailDto>(`${BASE_URL}/${id}`);
  }

  /** Create a DRAFT summary. 409 on domain conflicts. */
  create(req: PayrollSummaryCreateRequest): Observable<PayrollSummaryDetailDto> {
    return this.http
      .post<PayrollSummaryDetailDto>(BASE_URL, req)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  /** Update a DRAFT summary. 409 on SUMMARY_NOT_DRAFT or assignment rules. */
  update(id: string, req: PayrollSummaryUpdateRequest): Observable<PayrollSummaryDetailDto> {
    return this.http
      .put<PayrollSummaryDetailDto>(`${BASE_URL}/${id}`, req)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  /** Delete a DRAFT summary. 409 on SUMMARY_NOT_DRAFT. */
  delete(id: string): Observable<void> {
    return this.http
      .delete<void>(`${BASE_URL}/${id}`)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  /** Validate a single DRAFT. Idempotent on already-VALIDATED. */
  validate(id: string): Observable<PayrollSummaryDetailDto> {
    return this.http
      .post<PayrollSummaryDetailDto>(`${BASE_URL}/${id}/validate`, {})
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  /**
   * Bulk-validate every DRAFT in a period matching the filters.
   * Always returns 200 with a result envelope; failures are reported
   * per row rather than via a non-2xx status.
   */
  validateAll(req: PayrollSummaryValidateRequest): Observable<ValidateAllResult> {
    return this.http.post<ValidateAllResult>(`${BASE_URL}/validate-all`, req);
  }
}

/**
 * Serializes criteria to HttpParams.
 *
 * - `rangeStart` and `rangeEnd` are always sent (required by the backend).
 * - `page` and `size` are always sent.
 * - The four `any*` filters are comma-joined into a single value
 *   (`?anyCountryIds=c1,c2`). Spring binds this into a Set<UUID> via its
 *   default String-to-collection converter.
 * - Other optional fields are sent only when set.
 */
function toHttpParams(criteria: PayrollSummaryCriteria): HttpParams {
  let params = new HttpParams()
    .set('rangeStart', criteria.rangeStart)
    .set('rangeEnd', criteria.rangeEnd)
    .set('page', criteria.page.toString())
    .set('size', criteria.size.toString());

  if (criteria.employeeId) {
    params = params.set('employeeId', criteria.employeeId);
  }
  if (criteria.employeeMatricule) {
    params = params.set('employeeMatricule', criteria.employeeMatricule);
  }
  if (criteria.status) {
    params = params.set('status', criteria.status);
  }
  if (criteria.payrollFrequency) {
    params = params.set('payrollFrequency', criteria.payrollFrequency);
  }
  if (criteria.sortBy) {
    params = params.set('sortBy', criteria.sortBy);
  }
  if (criteria.sortDirection) {
    params = params.set('sortDirection', criteria.sortDirection);
  }

  if (criteria.anyCountryIds?.length) {
    params = params.set('anyCountryIds', criteria.anyCountryIds.join(','));
  }
  if (criteria.anyDepartmentIds?.length) {
    params = params.set('anyDepartmentIds', criteria.anyDepartmentIds.join(','));
  }
  if (criteria.anyRoleIds?.length) {
    params = params.set('anyRoleIds', criteria.anyRoleIds.join(','));
  }
  if (criteria.anyEmploymentTypes?.length) {
    params = params.set('anyEmploymentTypes', criteria.anyEmploymentTypes.join(','));
  }

  return params;
}
