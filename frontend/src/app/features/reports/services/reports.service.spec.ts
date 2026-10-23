import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReportsService } from './reports.service';
import { AggregateReportRequest, AggregateReportResponse } from '../models';
import { ReportsError } from './reports-error';
import { apiUrl } from '@core/config/api-base-url';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';

describe('ReportsService', () => {
  let service: ReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReportsService,
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate aggregate report successfully', (done) => {
    const mockRequest: AggregateReportRequest = {
      dateRange: { startDate: '2026-01-01', endDate: '2026-01-31' },
      groupKeys: ['DEPARTMENT'],
      anyCountryIds: null,
      anyDepartmentIds: null,
      anyRoleIds: null,
      anyEmploymentTypes: null,
      includeBreakdown: true,
      includeStatistics: true,
    };

    const mockResponse: AggregateReportResponse = {
      referenceCurrency: 'USD',
      groupKeys: ['DEPARTMENT'],
      lines: [],
      kind: 'AGGREGATE',
      dateRange: {startDate: "2026-01-01", endDate:"2026-03-31"}
    };

    service.generateAggregateReport(mockRequest).subscribe((res) => {
      expect(res).toEqual(mockResponse);
      done();
    });

    const req = httpMock.expectOne(apiUrl('/api/v1/reports/aggregate'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockRequest);
    req.flush(mockResponse);
  });

  it('should parse REPORT_BLOCKED error into structured warnings', (done) => {
    const mockRequest: AggregateReportRequest = {
      dateRange: { startDate: '2026-01-01', endDate: '2026-01-31' },
      groupKeys: ['DEPARTMENT'],
      anyCountryIds: null,
      anyDepartmentIds: null,
      anyRoleIds: null,
      anyEmploymentTypes: null,
      includeBreakdown: true,
      includeStatistics: true,
    };

    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'UNVALIDATED_PAYROLL_SUMMARY',
          message: 'Some employees have a non-VALIDATED payroll summary',
          severity: 'BLOCKER',
          details: {
            count: 1,
            employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
          },
        },
      ],
    };

    service.generateAggregateReport(mockRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err).toBeDefined();
        expect(err.code).toBe('REPORT_BLOCKED');
        expect(err.warnings.length).toBe(1);
        expect(err.warnings[0].code).toBe('UNVALIDATED_PAYROLL_SUMMARY');
        expect(err.warnings[0].countConcerned).toBe(1);
        expect(err.warnings[0].employees.length).toBe(1);
        expect(err.warnings[0].employees[0].matricule).toBe('EMP-001');
        done();
      },
    });

    const req = httpMock.expectOne(apiUrl('/api/v1/reports/aggregate'));
    req.flush(errorResponse, { status: 400, statusText: 'Bad Request' });
  });
});
