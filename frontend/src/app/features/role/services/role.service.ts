import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DataPage } from '@core/models/data-page.model';
import { RoleData } from '../models/role.model';
import { apiUrl } from '@core/config/api-base-url';

@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly http = inject(HttpClient);

  search(keyword: string = '', page = 0, size = 50): Observable<DataPage<RoleData>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', 'name')
      .set('sortDirection', 'ASC');

    if (keyword) {
      params = params.set('keyword', keyword);
    }

    return this.http.get<DataPage<RoleData>>(apiUrl('/api/v1/roles'), { params });
  }
}
