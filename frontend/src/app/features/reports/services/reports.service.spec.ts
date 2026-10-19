import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ToastService } from '../../../core/ui/toast/toast.service';
import { httpErrorInterceptor } from '../../../core/http/http-error.interceptor';
import {
  AggregateReportRequest,
  AggregateReportResponse,
} from '../models';
import { ReportsError } from './reports-error';
import { ReportsService } from './reports.service';

const URL = '/api/v1/reports/aggregate';

const validRequest: AggregateReportRequest = {
  dateRange: { startDate: '2026-01-01', endDate: '2026-03-31' },
  groupKeys: ['COUNTRY'],
  anyCountryIds: null,
  anyDepartmentIds: null,
  anyRoleIds: null,
  anyEmploymentTypes: null,
  includeBreakdown: false,
  includeStatistics: false,
};

describe('ReportsService', () => {
  let service: ReportsService;
  let http: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success',
      'error',
      'info',
      'warning',
      'dismiss',
    ]);

    TestBed.configureTestingModule({
      providers: [
        { provide: ToastService, useValue: toast },
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(ReportsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // --- Success --------------------------------------------------------------

  it('POSTs to the aggregate endpoint with the request body as-is', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      next: (res) => {
        expect(res.kind).toBe('AGGREGATE');
        expect(res.lines.length).toBe(1);
        expect(res.warnings).toEqual([]);
        done();
      },
      error: () => done.fail('should not error'),
    });

    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(validRequest);
    // `null` filters must survive JSON serialization as `null`, not `[]`.
    expect(req.request.body.anyCountryIds).toBeNull();
    req.flush(successResponse());
  });

  // --- VALIDATION_FAILED ----------------------------------------------------

  it('maps a 400 with VALIDATION_FAILED to ReportsError with fieldErrors, empty blockers', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('VALIDATION_FAILED');
        expect(err.status).toBe(400);
        expect(err.fieldErrors.length).toBe(2);
        expect(err.fieldErrors[0].field).toBe('dateRange.startDate');
        expect(err.blockers).toEqual([]);
        // The interceptor must not toast on 4xx with a parseable body.
        expect(toast.error).not.toHaveBeenCalled();
        done();
      },
    });

    const req = http.expectOne(URL);
    req.flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        errors: [
          {
            field: 'dateRange.startDate',
            code: 'NOT_NULL',
            message: 'startDate is required',
            attributes: {},
          },
          {
            field: 'dateRange.endDate',
            code: 'INVALID_RANGE',
            message: 'endDate must be >= startDate',
            attributes: { startDate: '2026-03-31' },
          },
        ],
        timestamp: '2026-10-19T12:00:00Z',
      },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  // --- REPORT_BLOCKED -------------------------------------------------------

  it('maps a 400 with REPORT_BLOCKED to ReportsError with blockers populated', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('REPORT_BLOCKED');
        expect(err.status).toBe(400);
        expect(err.fieldErrors.length).toBe(1);
        expect(err.blockers.length).toBe(1);

        const blocker = err.blockers[0];
        expect(blocker.severity).toBe('BLOCKER');
        expect(blocker.code).toBe('MISSING_CURRENCY');
        expect(blocker.message).toBe(
          'Employee #42 has no currency set; cannot aggregate.',
        );
        expect(blocker.details).toEqual({
          field: 'employees[42].currency',
          attributes: { employeeId: '42' },
        });

        expect(toast.error).not.toHaveBeenCalled();
        done();
      },
    });

    const req = http.expectOne(URL);
    req.flush(
      {
        code: 'REPORT_BLOCKED',
        message: 'Report could not be generated',
        errors: [
          {
            field: 'employees[42].currency',
            code: 'MISSING_CURRENCY',
            message: 'Employee #42 has no currency set; cannot aggregate.',
            attributes: { employeeId: '42' },
          },
        ],
        timestamp: '2026-10-19T12:00:00Z',
      },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  it('leaves blockers empty for VALIDATION_FAILED even when errors are present', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('VALIDATION_FAILED');
        expect(err.fieldErrors.length).toBe(1);
        expect(err.blockers).toEqual([]);
        done();
      },
    });

    const req = http.expectOne(URL);
    req.flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        errors: [
          {
            field: 'groupKeys',
            code: 'NOT_EMPTY',
            message: 'groupKeys must not be empty',
            attributes: {},
          },
        ],
        timestamp: '2026-10-19T12:00:00Z',
      },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  // --- Non-report 4xx / 5xx -------------------------------------------------

  it('maps a 500 with no parseable body to SERVER_ERROR', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('SERVER_ERROR');
        expect(err.status).toBe(500);
        expect(err.fieldErrors).toEqual([]);
        expect(err.blockers).toEqual([]);
        // The interceptor toasts on 5xx (no parseable body).
        expect(toast.error).toHaveBeenCalledOnceWith(
          jasmine.stringMatching(/server encountered an error/i),
        );
        done();
      },
    });

    const req = http.expectOne(URL);
    req.flush('boom', { status: 500, statusText: 'Server Error' });
  });

  it('maps a network failure (status 0) to NETWORK_ERROR', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('NETWORK_ERROR');
        expect(err.status).toBe(0);
        expect(err.blockers).toEqual([]);
        expect(toast.error).toHaveBeenCalled();
        done();
      },
    });

    const req = http.expectOne(URL);
    req.error(new ProgressEvent('error'));
  });

  // --- Edge: 4xx with an unexpected body shape ------------------------------

  it('maps a 400 with a non-conforming body to HTTP_ERROR', (done) => {
    service.generateAggregateReport(validRequest).subscribe({
      error: (err: ReportsError) => {
        expect(err.code).toBe('HTTP_ERROR');
        expect(err.status).toBe(400);
        expect(err.blockers).toEqual([]);
        done();
      },
    });

    const req = http.expectOne(URL);
    req.flush('not json', { status: 400, statusText: 'Bad Request' });
  });
});

// --- Fixtures ---------------------------------------------------------------

function successResponse(): AggregateReportResponse {
  return {
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-03-31' },
    groupKeys: ['COUNTRY'],
    referenceCurrency: 'EUR',
    lines: [
      {
        amount: { amount: 12000.5, currency: 'EUR' },
        employeeCount: 3,
        country: 'CM',
      },
    ],
    warnings: [],
  };
}
