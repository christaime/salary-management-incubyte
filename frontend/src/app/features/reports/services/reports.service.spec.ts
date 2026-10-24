import { TestBed } from '@angular/core/testing';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ReportsService } from './reports.service';
import { AggregateReportRequest, AggregateReportResponse } from '../models';
import { ReportsError } from './reports-error';
import { apiUrl } from '@core/config/api-base-url';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';

describe('ReportsService', () => {
  let service: ReportsService;
  let httpMock: HttpTestingController;

  const URL = apiUrl('/api/v1/reports/aggregate');

  const baseRequest: AggregateReportRequest = {
    dateRange: { startDate: '2026-01-01', endDate: '2026-01-31' },
    groupKeys: ['DEPARTMENT'],
    anyCountryIds: null,
    anyDepartmentIds: null,
    anyRoleIds: null,
    anyEmploymentTypes: null,
    includeBreakdown: true,
    includeStatistics: true,
  };

  const successResponse: AggregateReportResponse = {
    referenceCurrency: 'USD',
    groupKeys: ['DEPARTMENT'],
    lines: [],
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-03-31' },
  };

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

  // -----------------------------------------------------------------
  // creation + happy path
  // -----------------------------------------------------------------

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate aggregate report successfully', (done) => {
    service.generateAggregateReport(baseRequest).subscribe((res) => {
      expect(res).toEqual(successResponse);
      done();
    });

    const req = httpMock.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(baseRequest);
    req.flush(successResponse);
  });

  // -----------------------------------------------------------------
  // REPORT_BLOCKED — UNVALIDATED_PAYROLL_SUMMARY -> EmployeeWarning
  // -----------------------------------------------------------------

  it('should parse REPORT_BLOCKED with UNVALIDATED_PAYROLL_SUMMARY into an EmployeeWarning', (done) => {
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

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err).toBeDefined();
        expect(err.code).toBe('REPORT_BLOCKED');
        expect(err.warnings.length).toBe(1);

        const warning = err.warnings[0];
        if (warning.code !== 'UNVALIDATED_PAYROLL_SUMMARY') {
          fail('first warning should be UNVALIDATED_PAYROLL_SUMMARY');
          return;
        }
        expect(warning.severity).toBe('BLOCKER');
        expect(warning.countConcerned).toBe(1);
        expect(warning.employees.length).toBe(1);
        expect(warning.employees[0].matricule).toBe('EMP-001');
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // REPORT_BLOCKED — MISSING_PAYROLL_SUMMARY -> EmployeeWarning
  // -----------------------------------------------------------------

  it('should parse REPORT_BLOCKED with MISSING_PAYROLL_SUMMARY into an EmployeeWarning', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_PAYROLL_SUMMARY',
          message: 'Missing summaries',
          severity: 'WARNING',
          details: {
            count: 2,
            employees: [
              { id: '2', matricule: 'EMP-002', name: 'Jane Roe' },
              { id: '3', matricule: 'EMP-003', name: 'Amy Poe' },
            ],
          },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.code).toBe('REPORT_BLOCKED');
        expect(err.warnings.length).toBe(1);

        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_PAYROLL_SUMMARY') {
          fail('first warning should be MISSING_PAYROLL_SUMMARY');
          return;
        }
        expect(warning.severity).toBe('WARNING');
        expect(warning.countConcerned).toBe(2);
        expect(warning.employees.length).toBe(2);
        expect(warning.employees[0].matricule).toBe('EMP-002');
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // REPORT_BLOCKED — MISSING_FX_RATE -> MissingCurrencyRateWarning
  // -----------------------------------------------------------------

  it('should parse MISSING_FX_RATE into a MissingCurrencyRateWarning', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_FX_RATE',
          message: 'No FX rate for currency XAF',
          severity: 'BLOCKER',
          details: { currency: 'XAF', reason: 'currencyRate.not.found' },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.warnings.length).toBe(1);

        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_FX_RATE') {
          fail('first warning should be MISSING_FX_RATE');
          return;
        }
        expect(warning.severity).toBe('BLOCKER');
        expect(warning.currency).toBe('XAF');
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // REPORT_BLOCKED — MISSING_REFERENCE_CURRENCY -> MissingReferenceCurrencyWarning
  // -----------------------------------------------------------------

  it('should parse MISSING_REFERENCE_CURRENCY into a MissingReferenceCurrencyWarning', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_REFERENCE_CURRENCY',
          message: 'No reference defined',
          severity: 'BLOCKER',
          details: { reason: 'referenceCurrency.null' },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.warnings.length).toBe(1);

        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_REFERENCE_CURRENCY') {
          fail('first warning should be MISSING_REFERENCE_CURRENCY');
          return;
        }
        expect(warning.severity).toBe('BLOCKER');
        expect(warning.currency).toBeUndefined();
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // REPORT_BLOCKED — PERIOD_CLOSED -> PeriodClosedWarning
  // -----------------------------------------------------------------

  it('should parse PERIOD_CLOSED into a PeriodClosedWarning', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'PERIOD_CLOSED',
          message: 'Period is closed; snapshot is only built for open periods',
          severity: 'BLOCKER',
          details: { periodId: '55555555-5555-5555-5555-555555555555' },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.warnings.length).toBe(1);

        const warning = err.warnings[0];
        if (warning.code !== 'PERIOD_CLOSED') {
          fail('first warning should be PERIOD_CLOSED');
          return;
        }
        expect(warning.severity).toBe('BLOCKER');
        expect(warning.message).toBe(
          'Period is closed; snapshot is only built for open periods',
        );
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // Employee warning — more than 10 employees truncated
  // -----------------------------------------------------------------

  it('should cap employee details at 10 when more are returned', (done) => {
    const employees = Array.from({ length: 15 }, (_, i) => ({
      id: String(i + 1),
      matricule: `EMP-${String(i + 1).padStart(3, '0')}`,
      name: `Name ${i + 1}`,
    }));
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_PAYROLL_SUMMARY',
          message: 'Missing summaries',
          severity: 'WARNING',
          details: { count: 15, employees },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_PAYROLL_SUMMARY') {
          fail('first warning should be MISSING_PAYROLL_SUMMARY');
          return;
        }
        expect(warning.countConcerned).toBe(15);
        expect(warning.employees.length).toBe(10);
        expect(warning.employees[9].matricule).toBe('EMP-010');
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // Employee warning — countConcerned falls back to employees.length
  // -----------------------------------------------------------------

  it('should fall back to employees.length when details.count is absent', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_PAYROLL_SUMMARY',
          message: 'Missing summaries',
          severity: 'WARNING',
          details: {
            employees: [
              { id: '1', matricule: 'EMP-001', name: 'John Doe' },
              { id: '2', matricule: 'EMP-002', name: 'Jane Roe' },
              { id: '3', matricule: 'EMP-003', name: 'Amy Poe' },
            ],
          },
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_PAYROLL_SUMMARY') {
          fail('first warning should be MISSING_PAYROLL_SUMMARY');
          return;
        }
        expect(warning.countConcerned).toBe(3);
        expect(warning.employees.length).toBe(3);
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // Employee warning — details absent entirely
  // -----------------------------------------------------------------

  it('should produce an EmployeeWarning with empty employees when details is absent', (done) => {
    const errorResponse = {
      code: 'REPORT_BLOCKED',
      message: 'Report could not be generated',
      errors: [
        {
          code: 'MISSING_PAYROLL_SUMMARY',
          message: 'Missing summaries',
          severity: 'WARNING',
        },
      ],
    };

    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        const warning = err.warnings[0];
        if (warning.code !== 'MISSING_PAYROLL_SUMMARY') {
          fail('first warning should be MISSING_PAYROLL_SUMMARY');
          return;
        }
        expect(warning.countConcerned).toBe(0);
        expect(warning.employees).toEqual([]);
        done();
      },
    });

    httpMock.expectOne(URL).flush(errorResponse, {
      status: 400,
      statusText: 'Bad Request',
    });
  });

  // -----------------------------------------------------------------
  // Fallbacks
  // -----------------------------------------------------------------

  it('should fall back to NETWORK_OR_SERVER_ERROR on an empty error body', (done) => {
    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.code).toBe('NETWORK_OR_SERVER_ERROR');
        expect(err.warnings).toEqual([]);
        done();
      },
    });

    httpMock.expectOne(URL).flush({}, { status: 500, statusText: 'Server Error' });
  });

  it('should fall back to NETWORK_OR_SERVER_ERROR on a network error', (done) => {
    service.generateAggregateReport(baseRequest).subscribe({
      next: () => fail('should have failed with an error'),
      error: (err: ReportsError) => {
        expect(err.code).toBe('NETWORK_OR_SERVER_ERROR');
        expect(err.warnings).toEqual([]);
        done();
      },
    });

    httpMock.expectOne(URL).error(new ProgressEvent('error'));
  });
});
