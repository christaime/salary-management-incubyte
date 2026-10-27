import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '@core/config/api-base-url';
import { catchError, throwError } from 'rxjs';
import { ApiError, ApiDetailError} from '@core/http/api-error';
import { FormEditorError, FieldError } from '@core/models/form-editor-error.model';
import {
  EmployeeCriteria,
  EmployeeWithInEffectAssignmentDto,
  EmployeeSaveRequest
} from '../models';
import {
  DataPage,
} from '@core/models';

const EMPLOYEES_URL = apiUrl('/api/v1/employees');

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);

  /**
   * Search employees with pagination and filters.
   * Sends only the criteria fields that are set.
   */
  search(criteria: EmployeeCriteria): Observable<DataPage<EmployeeWithInEffectAssignmentDto>> {
    return this.http.get<DataPage<EmployeeWithInEffectAssignmentDto>>(
      EMPLOYEES_URL,
      { params: toHttpParams(criteria) },
    );
  }

  /** GET one employee with its in-effect assignment (for the editor). */
  getById(id: string): Observable<EmployeeWithInEffectAssignmentDto> {
    return this.http.get<EmployeeWithInEffectAssignmentDto>(`${EMPLOYEES_URL}/${id}`);
  }

  /** POST (create) or PUT (update) — decided by whether `id` is set on the payload. */
  save(request: EmployeeSaveRequest): Observable<EmployeeWithInEffectAssignmentDto> {
    const call$ = request.id
      ? this.http.put<EmployeeWithInEffectAssignmentDto>(`${EMPLOYEES_URL}/${request.id}`, request)
      : this.http.post<EmployeeWithInEffectAssignmentDto>(EMPLOYEES_URL, request);

    return call$.pipe(
      catchError((err: ApiError) => throwError(() => toFormEditorError(err))),
    );
  }

  delete(id: string): Observable<void> {
    return this.http
      .delete<void>(`${EMPLOYEES_URL}/${id}`)
      .pipe(catchError((err: ApiError) => throwError(() => toFormEditorError(err))));
  }
}

/**
 * The backend returns errors under `ApiError.details.errors` (array).
 * This unwraps them into the flat FormEditorError shape the editor uses.
 */
function toFormEditorError(api: ApiError): FormEditorError {
  console.log({api});
  const rawErrors: ApiDetailError[] = api.errors ?? [];
  const errors: FieldError[] = rawErrors
    .map((e) => ({
      field: String((e['details'] as any)?.field ?? ''),
      code: String(e['code'] ?? 'UNKNOWN'),
      message: String(e['message'] ?? 'Unknown error'),
    }))
    .filter((e) => e.field !== '');   // keep only field-level

  return {
    code: api.code,
    message: api.message,
    errors,
  };
}

/**
 * Converts the criteria object to HttpParams.
 * The `any*` sets are comma-joined for the backend.
 */
function toHttpParams(criteria: EmployeeCriteria): HttpParams {
  let params = new HttpParams();

  if (criteria.keyword) {
    params = params.set('keyword', criteria.keyword);
  }
  if (criteria.matricule) {
    params = params.set('matricule', criteria.matricule);
  }
  if (criteria.anyAssignmentCountryIds?.length) {
    params = params.set('anyAssignmentCountryIds', criteria.anyAssignmentCountryIds.join(','));
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
  if (criteria.sortBy) {
    params = params.set('sortBy', criteria.sortBy);
  }
  if (criteria.sortDirection) {
    params = params.set('sortDirection', criteria.sortDirection);
  }

  params = params.set('page', criteria.page.toString());
  params = params.set('size', criteria.size.toString());

  return params;
}
