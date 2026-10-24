import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import {
  AggregateReportFormValue,
} from '../../components/aggregate-report-form/aggregate-report-form.component';
import { AggregateReportRequest } from '../../models';
import { ToastService } from '../../../../core/ui/toast/toast.service';
import { AggregateReportPageComponent } from './aggregate-report.page';
import { apiUrl } from '@core/config/api-base-url';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { CountryService } from '@features/country/services/country.service';
import { DepartmentService } from '@features/department/services/department.service';
import { RoleService } from '@features/role/services/role.service';

const URL = '/api/v1/reports/aggregate';

const YEAR = 2026;
const PERIOD_JAN = {
  id: '2026-01-M',
  name: 'January 2026',
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  frequency: 'MONTHLY' as const,
  closed: true,
};

const PERIOD_SEP = {
  id: '2026-09-M',
  name: 'September 2026',
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  frequency: 'MONTHLY' as const,
  closed: true,
};

const formValue = (
  overrides: Partial<AggregateReportFormValue> = {},
): AggregateReportFormValue => ({
  startYear: YEAR,
  startPeriod: PERIOD_JAN,
  endPeriod: null,
  filters: {
    countryIds: null,
    departmentIds: null,
    roleIds: null,
    employmentTypes: null,
  },
  ...overrides,
});

function successBody(groupKeys: AggregateReportRequest['groupKeys']) {
  return {
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-01-31' },
    groupKeys,
    referenceCurrency: 'USD',
    lines: [
      {
        amount: { amount: 1000, currency: 'USD' },
        employeeCount: 2,
        country: 'CM',
      },
    ],
    warnings: [],
  };
}

function blockedBody() {
  return {
    code: 'REPORT_BLOCKED',
    message: 'Report could not be generated',
    errors: [
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: 'Some employees with an in-effect assignment have no payroll summary',
        severity: 'WARNING',
        details: {
          count: 1,
          employees: [{ id: '1', matricule: 'EMP-001', name: 'John Doe' }],
        },
      },
    ],
    timestamp: '2026-10-19T12:00:00Z',
  };
}

describe('AggregateReportPageComponent', () => {
  let fixture: ComponentFixture<AggregateReportPageComponent>;
  let component: AggregateReportPageComponent;
  let http: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );

    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success', 'error', 'info', 'warning', 'dismiss',
    ]);

    await TestBed.configureTestingModule({
      imports: [AggregateReportPageComponent, NoopAnimationsModule],
      providers: [
        { provide: ToastService, useValue: toast },
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: PayrollPeriodsService, useValue: spyPeriod },
        { provide: CountryService, useValue: spyCountry },
        { provide: DepartmentService, useValue: spyDepartment },
        { provide: RoleService, useValue: spyRole },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportPageComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  // -----------------------------------------------------------------
  // initial state
  // -----------------------------------------------------------------

  it('starts idle, TOTAL tab active, no results', () => {
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.showResults()).toBeFalse();
    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
  });

  // -----------------------------------------------------------------
  // submit → results
  // -----------------------------------------------------------------

  it('submits form and shows results for TOTAL', () => {
    component.onFormSubmit(formValue());

    const req = http.expectOne(apiUrl(URL));
    expect(req.request.method).toBe('POST');
    expect(req.request.body.groupKeys).toEqual([]);
    expect(req.request.body.dateRange).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-01-31',
    });
    expect(req.request.body.includeBreakdown).toBeTrue();

    req.flush(successBody([]));

    expect(component.state()).toBe('success');
    expect(component.showResults()).toBeTrue();
  });

  it('uses end period when provided', () => {
    component.onFormSubmit(formValue({ endPeriod: PERIOD_SEP }));

    const req = http.expectOne(apiUrl(URL));
    expect(req.request.body.dateRange).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-09-30',
    });
    req.flush(successBody([]));
  });

  // -----------------------------------------------------------------
  // kind toggle — cache hit
  // -----------------------------------------------------------------

  it('uses cached response on repeat toggle without new request', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));
    expect(component.state()).toBe('success');

    component.onKindChange('COUNTRY');
    http.expectOne(apiUrl(URL)).flush(successBody(['COUNTRY']));
    expect(component.reportResponse()!.groupKeys).toEqual(['COUNTRY']);

    component.onKindChange('TOTAL');
    http.expectNone(apiUrl(URL));
    expect(component.reportResponse()!.groupKeys).toEqual([]);
  });

  // -----------------------------------------------------------------
  // kind toggle — cache miss with a prior submit
  // -----------------------------------------------------------------

  it('auto-regenerates on toggle when a report was already generated', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));

    component.onKindChange('DEPARTMENT');
    const req = http.expectOne(apiUrl(URL));
    expect(req.request.body.groupKeys).toEqual(['DEPARTMENT']);
    req.flush(successBody(['DEPARTMENT']));

    expect(component.selectedKind()).toBe('DEPARTMENT');
    expect(component.reportResponse()!.groupKeys).toEqual(['DEPARTMENT']);
  });

  // -----------------------------------------------------------------
  // kind toggle — no prior submit (no fetch)
  // -----------------------------------------------------------------

  it('does nothing on toggle when no report was ever generated', () => {
    component.onKindChange('COUNTRY');
    http.expectNone(apiUrl(URL));
    expect(component.selectedKind()).toBe('COUNTRY');
    expect(component.state()).toBe('idle');
    expect(component.reportResponse()).toBeNull();
  });

  // -----------------------------------------------------------------
  // kind toggle — same kind, response present (no fetch)
  // -----------------------------------------------------------------

  it('does nothing on toggle when selecting the already-selected kind', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));

    component.onKindChange('TOTAL');
    http.expectNone(apiUrl(URL));
    expect(component.selectedKind()).toBe('TOTAL');
  });

  // -----------------------------------------------------------------
  // blockers
  // -----------------------------------------------------------------

  it('shows blocker banner on REPORT_BLOCKED and toasts', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(blockedBody(), {
      status: 400,
      statusText: 'Bad Request',
    });
    fixture.detectChanges();

    expect(component.state()).toBe('error');
    expect(component.showBlockerBanner()).toBeTrue();
    expect(component.blockers().length).toBe(1);
    expect(toast.error).toHaveBeenCalled();
  });

  it('dismissBanner clears error and returns to idle', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(blockedBody(), {
      status: 400,
      statusText: 'Bad Request',
    });
    component.dismissBanner();
    expect(component.state()).toBe('idle');
    expect(component.showBlockerBanner()).toBeFalse();
  });

  // -----------------------------------------------------------------
  // reload logic — onFormChange clears cache
  // -----------------------------------------------------------------

  it('onFormChange clears response, error, cache, state and selected kind', () => {
    // generate a report so there is something to clear
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));
    component.onKindChange('COUNTRY');
    http.expectOne(apiUrl(URL)).flush(successBody(['COUNTRY']));
    expect(component.reportResponse()).not.toBeNull();
    expect(component.state()).toBe('success');
    expect(component.selectedKind()).toBe('COUNTRY');

    // change the form → reset everything
    component.onFormChange();

    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.showResults()).toBeFalse();
    expect(component.showBlockerBanner()).toBeFalse();
  });

  it('onFormChange invalidates the cache so a subsequent toggle fetches again', () => {
    // first cycle: submit + switch to COUNTRY
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));
    component.onKindChange('COUNTRY');
    http.expectOne(apiUrl(URL)).flush(successBody(['COUNTRY']));

    // change the form → cache cleared
    component.onFormChange();

    // toggle back to TOTAL with no prior submit → no request, no cache hit
    component.onKindChange('TOTAL');
    http.expectNone(apiUrl(URL));
    expect(component.reportResponse()).toBeNull();

    // submit again → fresh request for TOTAL
    component.onFormSubmit(formValue());
    const req = http.expectOne(apiUrl(URL));
    expect(req.request.body.groupKeys).toEqual([]);
    req.flush(successBody([]));
    expect(component.state()).toBe('success');
  });

  it('onFormChange after a blocker clears the error banner', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(blockedBody(), {
      status: 400,
      statusText: 'Bad Request',
    });
    expect(component.showBlockerBanner()).toBeTrue();

    component.onFormChange();

    expect(component.reportError()).toBeNull();
    expect(component.showBlockerBanner()).toBeFalse();
    expect(component.state()).toBe('idle');
  });

  // -----------------------------------------------------------------
  // reload logic — onFormReset delegates to onFormChange
  // -----------------------------------------------------------------

  it('onFormReset clears cache, results and returns to TOTAL', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));
    component.onKindChange('COUNTRY');
    http.expectOne(apiUrl(URL)).flush(successBody(['COUNTRY']));
    expect(component.reportResponse()).not.toBeNull();

    component.onFormReset();

    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
    expect(component.showResults()).toBeFalse();
  });

  it('onFormReset leaves the page in a state where the next submit fetches fresh', () => {
    component.onFormSubmit(formValue());
    http.expectOne(apiUrl(URL)).flush(successBody([]));
    component.onFormReset();

    component.onFormSubmit(formValue());
    const req = http.expectOne(apiUrl(URL));
    expect(req.request.body.groupKeys).toEqual([]);
    req.flush(successBody([]));
    expect(component.state()).toBe('success');
  });
});
