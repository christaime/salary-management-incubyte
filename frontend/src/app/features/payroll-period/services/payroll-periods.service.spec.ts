import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DataPage } from '@core/models/data-page.model';
import { PayrollPeriodCriteria } from '../models/payroll-period-criteria.model';
import { PayrollPeriodDto } from '../models/payroll-period.model';
import { apiUrl } from '@core/config/api-base-url';

@Injectable({ providedIn: 'root' })
export class PayrollPeriodsService {
  private readonly http = inject(HttpClient);

  /**
   * Fetches a paginated list of payroll periods based on the provided criteria.
   * Maps the criteria object to HTTP query parameters.
   */
  list(criteria: PayrollPeriodCriteria = {}): Observable<DataPage<PayrollPeriodDto>> {
    let params = new HttpParams();

    if (criteria.keyword) params = params.set('keyword', criteria.keyword);
    if (criteria.startDate) params = params.set('startDate', criteria.startDate);
    if (criteria.endDate) params = params.set('endDate', criteria.endDate);
    if (criteria.frequency) params = params.set('frequency', criteria.frequency);

    if (criteria.isClosed !== undefined && criteria.isClosed !== null) {
      params = params.set('isClosed', criteria.isClosed.toString());
    }

    // Default pagination and sorting
    params = params.set('page', (criteria.page ?? 0).toString());
    params = params.set('size', (criteria.size ?? 20).toString());
    params = params.set('sortBy', criteria.sortBy ?? 'startDate');
    params = params.set('sortDirection', criteria.sortDirection ?? 'DESC');

    return this.http.get<DataPage<PayrollPeriodDto>>(
      apiUrl('/api/v1/payroll-periods'),
      { params }
    );
  }
}
