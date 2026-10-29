import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { apiUrl } from '@core/config/api-base-url';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';
import { ApiError } from '@core/http/api-error';

import { PayrollSummaryService } from './payroll-summary.service';
import { PayrollSummaryCriteria } from '../models/payroll-summary-criteria.model';
import {
  PayrollSummaryCreateRequest,
  PayrollSummaryValidateRequest,
} from '../models/payroll-summary-request.model';
import {
  PayrollSummaryDetailDto,
  PayrollSummaryDto,
} from '../models/payroll-summary.model';
import { FormEditorError } from '@core/models/form-editor-error.model';

const BASE = apiUrl('/api/v1/payroll-summaries');

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeSummaryRow(id = 's-1'): PayrollSummaryDto {
  return {
    id,
    status: 'DRAFT',
    employee: { id: 'e-1', matricule: 'EMP-0001', name: 'Ada Lovelace' },
    period: {
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false
    },
    country: { id: 'c-fr', code: 'FR', name: 'France' },
    department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    role: { id: 'r-swe', code: 'SWE', name: 'Software Engineer' },
    employmentType: 'FULL_TIME',
    amounts: {
      base: 1000,
      variable: 100,
      allowances: 50,
      employerTaxes: 200,
      total: 1350,
      currency: 'EUR',
    },
    assignment: { id: 'a-1', startDate: '2025-10-01', endDate: null, frequency: 'MONTHLY' },
  };
}

function makeSummaryDetail(id = 's-1'): PayrollSummaryDetailDto {
  return {
    ...makeSummaryRow(id),
    assignment: { id: 'a-1', startDate: '2025-10-01', endDate: null, hasPayroll: true },
  };
}

function baseCriteria(): PayrollSummaryCriteria {
  return {
    rangeStart: '2026-01-01',
    rangeEnd: '2026-03-31',
    page: 0,
    size: 20,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('PayrollSummaryService', () => {
  let service: PayrollSummaryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PayrollSummaryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // =======================================================================
  // search()
  // =======================================================================

  describe('search', () => {
    it('GETs /api/v1/payroll-summaries with range, page, and size', () => {
      service.search(baseCriteria()).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.get('rangeStart')).toBe('2026-01-01');
      expect(req.request.params.get('rangeEnd')).toBe('2026-03-31');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });

    it('does not send optional params that are unset', () => {
      service.search(baseCriteria()).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.has('employeeId')).toBeFalse();
      expect(req.request.params.has('employeeMatricule')).toBeFalse();
      expect(req.request.params.has('status')).toBeFalse();
      expect(req.request.params.has('sortBy')).toBeFalse();
      expect(req.request.params.has('sortDirection')).toBeFalse();
      expect(req.request.params.has('anyCountryIds')).toBeFalse();
      expect(req.request.params.has('anyDepartmentIds')).toBeFalse();
      expect(req.request.params.has('anyRoleIds')).toBeFalse();
      expect(req.request.params.has('anyEmploymentTypes')).toBeFalse();
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });

    it('comma-joins any* filters into a single value each', () => {
      service.search({
        ...baseCriteria(),
        anyCountryIds: ['c1', 'c2'],
        anyDepartmentIds: ['d1', 'd2', 'd3'],
        anyRoleIds: ['r1'],
        anyEmploymentTypes: ['FULL_TIME', 'PART_TIME'],
      }).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.get('anyCountryIds')).toBe('c1,c2');
      expect(req.request.params.get('anyDepartmentIds')).toBe('d1,d2,d3');
      expect(req.request.params.get('anyRoleIds')).toBe('r1');
      expect(req.request.params.get('anyEmploymentTypes')).toBe('FULL_TIME,PART_TIME');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });

    it('skips any* params when the arrays are empty', () => {
      service.search({
        ...baseCriteria(),
        anyCountryIds: [],
        anyDepartmentIds: [],
        anyRoleIds: [],
        anyEmploymentTypes: [],
      }).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.has('anyCountryIds')).toBeFalse();
      expect(req.request.params.has('anyDepartmentIds')).toBeFalse();
      expect(req.request.params.has('anyRoleIds')).toBeFalse();
      expect(req.request.params.has('anyEmploymentTypes')).toBeFalse();
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });

    it('sends employeeMatricule, status, sortBy, sortDirection when set', () => {
      service.search({
        ...baseCriteria(),
        employeeMatricule: 'EMP-0001',
        status: 'VALIDATED',
        sortBy: 'amounts.total',
        sortDirection: 'DESC',
      }).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.get('employeeMatricule')).toBe('EMP-0001');
      expect(req.request.params.get('status')).toBe('VALIDATED');
      expect(req.request.params.get('sortBy')).toBe('amounts.total');
      expect(req.request.params.get('sortDirection')).toBe('DESC');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });

    it('sends employeeId when set', () => {
      service.search({ ...baseCriteria(), employeeId: 'e-1' }).subscribe();

      const req = http.expectOne((r) => r.url === BASE && r.method === 'GET');
      expect(req.request.params.get('employeeId')).toBe('e-1');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0 });
    });
  });

  // =======================================================================
  // getById()
  // =======================================================================

  describe('getById', () => {
    it('GETs /api/v1/payroll-summaries/:id', () => {
      const summary = makeSummaryDetail();

      service.getById('s-1').subscribe((res) => expect(res).toEqual(summary));

      const req = http.expectOne(
        (r) => r.url === `${BASE}/s-1` && r.method === 'GET',
      );
      req.flush(summary);
    });
  });

  // =======================================================================
  // create()
  // =======================================================================

  describe('create', () => {
    it('POSTs /api/v1/payroll-summaries with the request body', () => {
      const body: PayrollSummaryCreateRequest = {
        employeeId: 'e-1',
        periodId: 'p-1',
        assignmentId: 'a-1',
        base: 1000,
        variable: 100,
        allowances: 50,
        employerTaxes: 200,
        total: 1350,
        currency: 'EUR',
      };

      service.create(body).subscribe();

      const req = http.expectOne(
        (r) => r.url === BASE && r.method === 'POST',
      );
      expect(req.request.body).toEqual(body);
      req.flush(makeSummaryDetail('s-new'), {
        status: 201,
        statusText: 'Created',
      });
    });

    it('maps a 409 ASSIGNMENT_NOT_IN_EFFECT to assignmentId', (done) => {
      service
        .create({
          employeeId: 'e-1',
          periodId: 'p-1',
          assignmentId: 'a-1',
          base: 1,
          variable: 0,
          allowances: 0,
          employerTaxes: 0,
          currency: 'EUR',
        })
        .subscribe({
          error: (err: FormEditorError) => {
            expect(err.code).toBe('CONFLICT');
            expect(err.errors.length).toBe(1);
            expect(err.errors[0].field).toBe('assignmentId');
            expect(err.errors[0].code).toBe('ASSIGNMENT_NOT_IN_EFFECT');
            done();
          },
        });

      const apiError: ApiError = {
        code: 'CONFLICT',
        message: 'Assignment not in effect',
        severity: 'ERROR',
        errors: [
          {
            code: 'ASSIGNMENT_NOT_IN_EFFECT',
            message: 'Assignment is not in effect',
            severity: 'ERROR',
          },
        ],
      } as any;

      http
        .expectOne((r) => r.url === BASE && r.method === 'POST')
        .flush(apiError, { status: 409, statusText: 'Conflict' });
    });

    it('maps a 409 SUMMARY_ALREADY_EXISTS to employeeId', (done) => {
      service
        .create({
          employeeId: 'e-1',
          periodId: 'p-1',
          assignmentId: 'a-1',
          base: 1,
          variable: 0,
          allowances: 0,
          employerTaxes: 0,
          currency: 'EUR',
        })
        .subscribe({
          error: (err: FormEditorError) => {
            expect(err.errors[0].field).toBe('employeeId');
            expect(err.errors[0].code).toBe('SUMMARY_ALREADY_EXISTS');
            done();
          },
        });

      const apiError: ApiError = {
        code: 'CONFLICT',
        message: 'Summary already exists',
        severity: 'ERROR',
        errors: [
          {
            code: 'SUMMARY_ALREADY_EXISTS',
            message: 'A payroll summary already exists for this employee and period',
            severity: 'ERROR',
          },
        ],
      } as any;

      http
        .expectOne((r) => r.url === BASE && r.method === 'POST')
        .flush(apiError, { status: 409, statusText: 'Conflict' });
    });
  });

  // =======================================================================
  // update()
  // =======================================================================

  describe('update', () => {
    it('PUTs /api/v1/payroll-summaries/:id', () => {
      const body = {
        assignmentId: 'a-1',
        base: 2000,
        variable: 200,
        allowances: 100,
        employerTaxes: 400,
        total: 2700,
        currency: 'EUR',
      };

      service.update('s-1', body).subscribe();

      const req = http.expectOne(
        (r) => r.url === `${BASE}/s-1` && r.method === 'PUT',
      );
      expect(req.request.body).toEqual(body);
      req.flush(makeSummaryDetail());
    });

    it('maps a 409 SUMMARY_NOT_DRAFT to a form-level error', (done) => {
      service
        .update('s-1', {
          assignmentId: 'a-1',
          base: 1,
          variable: 0,
          allowances: 0,
          employerTaxes: 0,
          currency: 'EUR',
        })
        .subscribe({
          error: (err: FormEditorError) => {
            expect(err.code).toBe('CONFLICT');
            expect(err.errors).toEqual([]); // SUMMARY_NOT_DRAFT → null field
            done();
          },
        });

      const apiError: ApiError = {
        code: 'CONFLICT',
        message: 'Summary is not a draft',
        severity: 'ERROR',
        errors: [
          {
            code: 'SUMMARY_NOT_DRAFT',
            message: 'This summary is validated',
            severity: 'ERROR',
          },
        ],
      } as any;

      http
        .expectOne((r) => r.url === `${BASE}/s-1` && r.method === 'PUT')
        .flush(apiError, { status: 409, statusText: 'Conflict' });
    });
  });

  // =======================================================================
  // delete()
  // =======================================================================

  describe('delete', () => {
    it('DELETEs /api/v1/payroll-summaries/:id', () => {
      service.delete('s-1').subscribe();

      const req = http.expectOne(
        (r) => r.url === `${BASE}/s-1` && r.method === 'DELETE',
      );
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('maps a 409 on delete', (done) => {
      service.delete('s-1').subscribe({
        error: (err: FormEditorError) => {
          expect(err.code).toBe('CONFLICT');
          done();
        },
      });

      const apiError: ApiError = {
        code: 'CONFLICT',
        message: 'Summary is not a draft',
        severity: 'ERROR',
        errors: [
          { code: 'SUMMARY_NOT_DRAFT', message: 'Not a draft', severity: 'ERROR' },
        ],
      } as any;

      http
        .expectOne((r) => r.url === `${BASE}/s-1` && r.method === 'DELETE')
        .flush(apiError, { status: 409, statusText: 'Conflict' });
    });
  });

  // =======================================================================
  // validate()
  // =======================================================================

  describe('validate', () => {
    it('POSTs /api/v1/payroll-summaries/:id/validate with an empty body', () => {
      service.validate('s-1').subscribe();

      const req = http.expectOne(
        (r) => r.url === `${BASE}/s-1/validate` && r.method === 'POST',
      );
      expect(req.request.body).toEqual({});
      req.flush(makeSummaryDetail());
    });

    it('maps a 409 PERIOD_CLOSED to periodId', (done) => {
      service.validate('s-1').subscribe({
        error: (err: FormEditorError) => {
          expect(err.errors[0].field).toBe('periodId');
          expect(err.errors[0].code).toBe('PERIOD_CLOSED');
          done();
        },
      });

      const apiError: ApiError = {
        code: 'CONFLICT',
        message: 'Period is closed',
        severity: 'ERROR',
        errors: [
          { code: 'PERIOD_CLOSED', message: 'Period is closed', severity: 'ERROR' },
        ],
      } as any;

      http
        .expectOne((r) => r.url === `${BASE}/s-1/validate` && r.method === 'POST')
        .flush(apiError, { status: 409, statusText: 'Conflict' });
    });
  });

  // =======================================================================
  // validateAll()
  // =======================================================================

  describe('validateAll', () => {
    it('POSTs /api/v1/payroll-summaries/validate-all with filters', () => {
      const body: PayrollSummaryValidateRequest = {
        periodId: 'p-1',
        anyCountryIds: ['c-fr'],
        anyEmploymentTypes: ['FULL_TIME'],
      };

      service.validateAll(body).subscribe((res) => {
        expect(res.total).toBe(15);
        expect(res.validated).toBe(13);
        expect(res.failed).toBe(2);
      });

      const req = http.expectOne(
        (r) => r.url === `${BASE}/validate-all` && r.method === 'POST',
      );
      expect(req.request.body).toEqual(body);
      req.flush({
        total: 15,
        validated: 13,
        failed: 2,
        failures: [
          { summaryId: 's-a', code: 'ASSIGNMENT_NOT_IN_EFFECT', message: 'Not in effect' },
          { summaryId: 's-b', code: 'PERIOD_CLOSED', message: 'Period closed' },
        ],
      });
    });

    it('does not throw when every row validates (failed = 0)', () => {
      service.validateAll({ periodId: 'p-1' }).subscribe((res) => {
        expect(res.failed).toBe(0);
        expect(res.failures).toEqual([]);
      });

      http
        .expectOne((r) => r.url === `${BASE}/validate-all` && r.method === 'POST')
        .flush({ total: 5, validated: 5, failed: 0, failures: [] });
    });

    it('returns the result even when some rows fail (best-effort)', () => {
      service.validateAll({ periodId: 'p-1' }).subscribe((res) => {
        expect(res.failed).toBeGreaterThan(0);
        expect(res.failures.length).toBe(res.failed);
        res.failures.forEach((f) => {
          expect(f.summaryId).toBeTruthy();
          expect(f.code).toBeTruthy();
          expect(f.message).toBeTruthy();
        });
      });

      http
        .expectOne((r) => r.url === `${BASE}/validate-all` && r.method === 'POST')
        .flush({
          total: 3,
          validated: 1,
          failed: 2,
          failures: [
            { summaryId: 's-a', code: 'ASSIGNMENT_NOT_IN_EFFECT', message: 'Not in effect' },
            { summaryId: 's-b', code: 'UNEXPECTED_ERROR', message: 'Something went wrong' },
          ],
        });
    });
  });
});
