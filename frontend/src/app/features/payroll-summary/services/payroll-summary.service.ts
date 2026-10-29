import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
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

  /**
   * Payroll selected from a "Non existent" list result. The list stashes
   * it here when the user clicks such a row; the editor consumes it in
   * create mode to pre-fill the form (via create, not update).
   */
  private readonly _selectedNonExistent =
    signal<PayrollSummaryDetailDto | null>(null);
  readonly selectedNonExistent = this._selectedNonExistent.asReadonly();

  setSelectedNonExistent(dto: PayrollSummaryDetailDto | null): void {
    this._selectedNonExistent.set(dto? {...dto, id: ""} : dto);
  }

  clearSelectedNonExistent(): void {
    this._selectedNonExistent.set(null);
  }

  search(criteria: PayrollSummaryCriteria): Observable<DataPage<PayrollSummaryDto>> {
    return this.http.get<DataPage<PayrollSummaryDto>>(BASE_URL, {
      params: toHttpParams(criteria),
    });
  }

  getById(id: string): Observable<PayrollSummaryDetailDto> {
    return this.http.get<PayrollSummaryDetailDto>(`${BASE_URL}/${id}`);
  }

  create(req: PayrollSummaryCreateRequest): Observable<PayrollSummaryDetailDto> {
    return this.http
      .post<PayrollSummaryDetailDto>(BASE_URL, req)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  update(id: string, req: PayrollSummaryUpdateRequest): Observable<PayrollSummaryDetailDto> {
    return this.http
      .put<PayrollSummaryDetailDto>(`${BASE_URL}/${id}`, req)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  delete(id: string): Observable<void> {
    return this.http
      .delete<void>(`${BASE_URL}/${id}`)
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  validate(id: string): Observable<PayrollSummaryDetailDto> {
    return this.http
      .post<PayrollSummaryDetailDto>(`${BASE_URL}/${id}/validate`, {})
      .pipe(catchError((err: ApiError) => throwError(() => toPayrollEditorError(err))));
  }

  validateAll(req: PayrollSummaryValidateRequest): Observable<ValidateAllResult> {
    return this.http.post<ValidateAllResult>(`${BASE_URL}/validate-all`, req);
  }
}

function toHttpParams(criteria: PayrollSummaryCriteria): HttpParams {
  let params = new HttpParams()
    .set('rangeStart', criteria.rangeStart)
    .set('rangeEnd', criteria.rangeEnd)
    .set('page', criteria.page.toString())
    .set('size', criteria.size.toString());

  if (criteria.employeeId) params = params.set('employeeId', criteria.employeeId);
  if (criteria.employeeMatricule) params = params.set('employeeMatricule', criteria.employeeMatricule);
  if (criteria.status) params = params.set('status', criteria.status);
  if (criteria.nonExistent) params = params.set('nonExistent', 'true');
  if (criteria.payrollFrequency) params = params.set('payrollFrequency', criteria.payrollFrequency);
  if (criteria.sortBy) params = params.set('sortBy', criteria.sortBy);
  if (criteria.sortDirection) params = params.set('sortDirection', criteria.sortDirection);

  if (criteria.anyCountryIds?.length) params = params.set('anyCountryIds', criteria.anyCountryIds.join(','));
  if (criteria.anyDepartmentIds?.length) params = params.set('anyDepartmentIds', criteria.anyDepartmentIds.join(','));
  if (criteria.anyRoleIds?.length) params = params.set('anyRoleIds', criteria.anyRoleIds.join(','));
  if (criteria.anyEmploymentTypes?.length) params = params.set('anyEmploymentTypes', criteria.anyEmploymentTypes.join(','));

  return params;
}
