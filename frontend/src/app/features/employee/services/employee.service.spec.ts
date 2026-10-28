import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { apiUrl } from '@core/config/api-base-url';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';
import { EmployeeService } from './employee.service';
import {
  EmployeeCriteria,
  EmployeeSaveRequest,
  EmployeeWithInEffectAssignmentDto,
} from '../models';
import { ApiError } from '@core/http/api-error';
import { FormEditorError } from '@core/models/form-editor-error.model';

// --- fixtures (unchanged) ---

function makeEmployee(id = 'e-1'): EmployeeWithInEffectAssignmentDto {
  return {
    id,
    matricule: 'EMP-000001',
    name: 'Employee One',
    email: 'e1@acme.test',
    birthDate: '1990-01-01',
    originCountry: { id: 'c-ch', code: 'CH', name: 'Switzerland' },
    assignmentInEffect: {
      id: 'a-1',
      startDate: '2021-04-01',
      endDate: null,
      country: { id: 'c-us', code: 'US', name: 'United States' },
      department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
      role: { id: 'r-cto', code: 'CTO', name: 'CTO' },
      employmentType: 'FULL_TIME',
      payrollFrequency: 'MONTHLY',
    },
  };
}

function makeCreateRequest(): EmployeeSaveRequest {
  return {
    id: null,
    matricule: 'EMP-000001',
    name: 'New Guy',
    email: 'new@acme.test',
    birthDate: '1995-01-01',
    originCountryId: 'c-ch',
    assignmentInEffect: {
      id: null,
      countryId: 'c-us',
      departmentId: 'd-eng',
      roleId: 'r-cto',
      employmentType: 'FULL_TIME',
      payrollFrequency: 'MONTHLY',
      startDate: '2026-01-01',
      endDate: null,
    },
    toClose: null,
  };
}

function makeUpdateRequest(id: string): EmployeeSaveRequest {
  return { ...makeCreateRequest(), id };
}

// --- tests ---

describe('EmployeeService', () => {
  let service: EmployeeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(EmployeeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ---------------- search ----------------

  describe('search', () => {
    it('GETs /api/v1/employees with page and size as strings', () => {
      service.search({ page: 0, size: 10 } as EmployeeCriteria).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'GET',
      );
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('10');
      expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
      req.flush({ content: [], totalElements: 0, totalPages: 0, first: true, last: true, empty: true, page: 0, size: 10 });
    });

    it('adds keyword when set', () => {
      service.search({ keyword: 'alice', page: 1, size: 25 }).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'GET',
      );
      expect(req.request.params.get('keyword')).toBe('alice');
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('25');
      req.flush({ content: [], totalElements: 0, totalPages: 0, first: true, last: true, empty: true, page: 1, size: 25 });
    });

    it('comma-joins anyAssignmentCountryIds / anyDepartmentIds / anyRoleIds / anyEmploymentTypes', () => {
      service.search({
        anyAssignmentCountryIds: ['c1', 'c2'],
        anyDepartmentIds: ['d1'],
        anyRoleIds: ['r1', 'r2', 'r3'],
        anyEmploymentTypes: ['FULL_TIME', 'CONTRACTOR'],
        page: 0,
        size: 10,
      }).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'GET',
      );
      expect(req.request.params.get('anyAssignmentCountryIds')).toBe('c1,c2');
      expect(req.request.params.get('anyDepartmentIds')).toBe('d1');
      expect(req.request.params.get('anyRoleIds')).toBe('r1,r2,r3');
      expect(req.request.params.get('anyEmploymentTypes')).toBe('FULL_TIME,CONTRACTOR');
      req.flush({ content: [], totalElements: 0, totalPages: 0, first: true, last: true, empty: true, page: 0, size: 10 });
    });

    it('skips empty arrays (does not send empty params)', () => {
      service.search({
        anyAssignmentCountryIds: [],
        anyDepartmentIds: [],
        anyRoleIds: [],
        anyEmploymentTypes: [],
        page: 0,
        size: 10,
      }).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'GET',
      );
      expect(req.request.params.has('anyAssignmentCountryIds')).toBeFalse();
      expect(req.request.params.has('anyDepartmentIds')).toBeFalse();
      expect(req.request.params.has('anyRoleIds')).toBeFalse();
      expect(req.request.params.has('anyEmploymentTypes')).toBeFalse();
      req.flush({ content: [], totalElements: 0, totalPages: 0, first: true, last: true, empty: true, page: 0, size: 10 });
    });

    it('adds sortBy and sortDirection when set', () => {
      service.search({ sortBy: 'name', sortDirection: 'DESC', page: 0, size: 10 }).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'GET',
      );
      expect(req.request.params.get('sortBy')).toBe('name');
      expect(req.request.params.get('sortDirection')).toBe('DESC');
      req.flush({ content: [], totalElements: 0, totalPages: 0, first: true, last: true, empty: true, page: 0, size: 10 });
    });
  });

  // ---------------- getById ----------------

  describe('getById', () => {
    it('GETs /api/v1/employees/:id', () => {
      const employee = makeEmployee();

      service.getById('e-1').subscribe((res) => expect(res).toEqual(employee));

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees/e-1') && r.method === 'GET',
      );
      req.flush(employee);
    });
  });

  // ---------------- save ----------------

  describe('save', () => {
    it('POSTs when request.id is null', () => {
      const payload = makeCreateRequest();

      service.save(payload).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      expect(req.request.body).toEqual(payload);
      req.flush(makeEmployee('e-new'));
    });

    it('PUTs when request.id is set', () => {
      const payload = makeUpdateRequest('e-1');

      service.save(payload).subscribe();

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees/e-1') && r.method === 'PUT',
      );
      expect(req.request.body).toEqual(payload);
      req.flush(makeEmployee('e-1'));
    });
  });

  // ---------------- save error mapping ----------------

  describe('save error mapping', () => {
    it('maps VALIDATION_FAILED with details.field to field errors', (done) => {
      const apiError: ApiError = {
        code: 'VALIDATION_FAILED',
        message: 'One or more fields are invalid',
        severity: 'ERROR',
        errors: [
          {
            code: 'Conflict',
            message: 'matricule already in use: EMP-000001',
            severity: 'ERROR',
            details: { field: 'matricule', attributes: {} },
          },
        ],
      };

      service.save(makeCreateRequest()).subscribe({
        error: (err: FormEditorError) => {
          expect(err.code).toBe('VALIDATION_FAILED');
          expect(err.message).toBe('One or more fields are invalid');
          expect(err.errors.length).toBe(1);
          expect(err.errors[0]).toEqual({
            field: 'matricule',
            code: 'Conflict',
            message: 'matricule already in use: EMP-000001',
          });
          done();
        },
      });

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      req.flush(apiError, { status: 400, statusText: 'Bad Request' });
    });

    it('keeps multiple field errors', (done) => {
      const apiError: ApiError = {
        code: 'VALIDATION_FAILED',
        message: 'One or more fields are invalid',
        severity: 'ERROR',
        errors: [
          { code: 'Conflict', message: 'matricule already in use', severity: 'ERROR', details: { field: 'matricule' } },
          { code: 'Email', message: 'email is not valid', severity: 'ERROR', details: { field: 'email' } },
          { code: 'NotNull', message: 'placement.startDate is required', severity: 'ERROR', details: { field: 'placement.startDate' } },
        ],
      };

      service.save(makeCreateRequest()).subscribe({
        error: (err: FormEditorError) => {
          expect(err.errors.length).toBe(3);
          expect(err.errors.map((e) => e.field)).toEqual([
            'matricule', 'email', 'placement.startDate',
          ]);
          done();
        },
      });

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      req.flush(apiError, { status: 400, statusText: 'Bad Request' });
    });

    it('drops entries without a details.field', (done) => {
      const apiError: ApiError = {
        code: 'VALIDATION_FAILED',
        message: 'Invalid',
        severity: 'ERROR',
        errors: [
          { code: 'Conflict', message: 'matricule already in use', severity: 'ERROR', details: { field: 'matricule' } },
          { code: 'General', message: 'Something else', severity: 'ERROR' } as any,
        ],
      };

      service.save(makeCreateRequest()).subscribe({
        error: (err: FormEditorError) => {
          expect(err.errors.length).toBe(1);
          expect(err.errors[0].field).toBe('matricule');
          done();
        },
      });

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      req.flush(apiError, { status: 400, statusText: 'Bad Request' });
    });

    it('falls back to UNKNOWN / empty array when details.errors is missing', (done) => {
      const apiError: ApiError = {
        code: 'NETWORK_OR_SERVER_ERROR',
        message: 'Boom',
        severity: 'ERROR',
      };

      service.save(makeCreateRequest()).subscribe({
        error: (err: FormEditorError) => {
          expect(err.code).toBe('NETWORK_OR_SERVER_ERROR');
          expect(err.message).toBe('Boom');
          expect(err.errors).toEqual([]);
          done();
        },
      });

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      req.flush(apiError, { status: 500, statusText: 'Server Error' });
    });

    it('defaults code to UNKNOWN when a field error has none', (done) => {
      const apiError: ApiError = {
        code: 'VALIDATION_FAILED',
        message: 'Invalid',
        severity: 'ERROR',
        errors: [
          { message: 'matricule already in use', details: { field: 'matricule' } } as any,
        ],
      };

      service.save(makeCreateRequest()).subscribe({
        error: (err: FormEditorError) => {
          expect(err.errors[0].code).toBe('UNKNOWN');
          done();
        },
      });

      const req = http.expectOne(
        (r) => r.url === apiUrl('/api/v1/employees') && r.method === 'POST',
      );
      req.flush(apiError, { status: 400, statusText: 'Bad Request' });
    });
  });
});
