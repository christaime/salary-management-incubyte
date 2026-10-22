import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DataPage } from '@core/models/data-page.model';
import { CountryData } from '../models/country.model';
import { apiUrl } from '@core/config/api-base-url';

@Injectable({ providedIn: 'root' })
export class CountryService {
  private readonly http = inject(HttpClient);

  search(keyword: string = '', page = 0, size = 50): Observable<DataPage<CountryData>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', 'name')
      .set('sortDirection', 'ASC');

    if (keyword) {
      params = params.set('keyword', keyword);
    }

    return this.http.get<DataPage<CountryData>>(apiUrl('/api/v1/countries'), { params });
  }
}
