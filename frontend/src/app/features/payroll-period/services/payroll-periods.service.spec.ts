import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PayrollPeriodsService } from './payroll-periods.service';
import { apiUrl } from '@core/config/api-base-url';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';

describe('PayrollPeriodsService', () => {
  let service: PayrollPeriodsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PayrollPeriodsService,
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PayrollPeriodsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch payroll periods with criteria and default sorting/pagination parameters', () => {
    service.search({ keyword: 'January', page: 0, size: 20 }).subscribe({
      next: (res) => {
        expect(res).toBeDefined();
      },
    });

    const req = httpMock.expectOne(
      (r) => r.url === apiUrl('/api/v1/payroll-periods') && r.params.get('keyword') === 'January'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sortBy')).toBe('startDate');
    expect(req.request.params.get('sortDirection')).toBe('DESC');

    req.flush({ content: [], totalElements: 0 });
  });
});
