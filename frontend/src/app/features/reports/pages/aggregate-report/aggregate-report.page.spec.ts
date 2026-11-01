import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';

import { AggregateReportFormValue } from '../../components/aggregate-report-form/aggregate-report-form.component';
import { BlockerBannerComponent } from '../../components/blocker-banner/blocker-banner.component';
import { WarningBannerComponent } from '../../components/warning-banner/warning-banner.component';
import { AggregateReportRequest, AggregateReportResponse } from '../../models';
import { ReportsService } from '../../services/reports.service';
import { ReportsError } from '../../services/reports-error';
import { ToastService } from '../../../../core/ui/toast/toast.service';
import { AggregateReportPageComponent } from './aggregate-report.page';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { CountryService } from '@features/country/services/country.service';
import { DepartmentService } from '@features/department/services/department.service';
import { RoleService } from '@features/role/services/role.service';
import { RefDto } from '@core/models';

const YEAR = 2026;
const CURRENT_YEAR = new Date().getFullYear();

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

const COUNTRY_CM: RefDto = { id: 'c-cm', code: 'CM', name: 'Cameroon' };
const COUNTRY_FR: RefDto = { id: 'c-fr', code: 'FR', name: 'France' };
const DEPT_ENG: RefDto = { id: 'd-eng', code: 'ENG', name: 'Engineering' };
const ROLE_SWE: RefDto = { id: 'r-swe', code: 'SWE', name: 'Software Engineer' };

const formValue = (
  overrides: Partial<AggregateReportFormValue> = {},
): AggregateReportFormValue => ({
  startYear: YEAR,
  startPeriod: PERIOD_JAN,
  endPeriod: null,
  filters: {
    countries: null,
    departments: null,
    roles: null,
    employmentTypes: null,
  },
  ...overrides,
});

function successBody(
  groupKeys: AggregateReportRequest['groupKeys'],
  warnings: any[] = [],
): AggregateReportResponse {
  return {
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-01-31' },
    groupKeys,
    referenceCurrency: 'USD',
    lines: [
      { amount: { amount: 1000, currency: 'USD' }, employeeCount: 2, country: 'CM' },
    ],
    warnings,
  } as any;
}

function makeReportsError(code: string): ReportsError {
  return {
    code,
    message: 'Report could not be generated',
    warnings: [
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message:
          'Some employees with an in-effect assignment have no payroll summary',
        severity: 'WARNING',
      } as any,
    ],
  } as any;
}

/** Reads the child `BlockerBannerComponent` instance, if rendered. */
function blockerBannerEl(fixture: ComponentFixture<AggregateReportPageComponent>) {
  return fixture.debugElement.query(By.directive(BlockerBannerComponent));
}
/** Reads the child `WarningBannerComponent` instance, if rendered. */
function warningBannerEl(fixture: ComponentFixture<AggregateReportPageComponent>) {
  return fixture.debugElement.query(By.directive(WarningBannerComponent));
}

describe('AggregateReportPageComponent', () => {
  let fixture: ComponentFixture<AggregateReportPageComponent>;
  let component: AggregateReportPageComponent;
  let reports: jasmine.SpyObj<ReportsService>;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    const emptyPage = {
      content: [],
      totalElements: 0,
      totalPages: 1,
      size: 20,
      number: 0,
    };
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(of(emptyPage));
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(of(emptyPage));
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(of(emptyPage));
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(of(emptyPage));

    reports = jasmine.createSpyObj<ReportsService>('ReportsService', [
      'generateAggregateReport',
    ]);

    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success',
      'error',
      'info',
      'warning',
      'dismiss',
    ]);

    await TestBed.configureTestingModule({
      imports: [AggregateReportPageComponent, NoopAnimationsModule],
      providers: [
        { provide: ToastService, useValue: toast },
        { provide: ReportsService, useValue: reports },
        { provide: PayrollPeriodsService, useValue: spyPeriod },
        { provide: CountryService, useValue: spyCountry },
        { provide: DepartmentService, useValue: spyDepartment },
        { provide: RoleService, useValue: spyRole },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // -----------------------------------------------------------------
  // initial state
  // -----------------------------------------------------------------

  it('starts idle, TOTAL tab active, no results', () => {
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.showResults()).toBeFalse();
    expect(component.isLoading()).toBeFalse();
    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
  });

  it('renders the child banners with no blockers/warnings on idle', () => {
    expect(blockerBannerEl(fixture)).toBeTruthy();
    expect(warningBannerEl(fixture)).toBeTruthy();
    expect(component.blockers().length).toBe(0);
    expect(component.warnings().length).toBe(0);
  });

  // -----------------------------------------------------------------
  // buildRequest — via the spy's captured argument
  // -----------------------------------------------------------------

  it('submits form and maps the request body for TOTAL', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(formValue());

    expect(reports.generateAggregateReport).toHaveBeenCalledTimes(1);
    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;

    expect(body.groupKeys).toEqual([]);
    expect(body.dateRange).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-01-31',
    });
    expect(body.includeBreakdown).toBeTrue();
    expect(body.includeStatistics).toBeTrue();
    expect(body.anyCountryIds).toBeNull();
    expect(body.anyDepartmentIds).toBeNull();
    expect(body.anyRoleIds).toBeNull();
    expect(body.anyEmploymentTypes).toBeNull();

    expect(component.state()).toBe('success');
    expect(component.showResults()).toBeTrue();
  });

  it('uses end period when provided', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(formValue({ endPeriod: PERIOD_SEP }));

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.dateRange).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-09-30',
    });
  });

  it('falls back to year bounds when no period is selected', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(
      formValue({ startPeriod: null, endPeriod: null, startYear: 2024 }),
    );

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.dateRange).toEqual({
      startDate: '2024-01-01',
      endDate: '2024-12-31',
    });
  });

  it('falls back to today when the year is the current year', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));
    const today = new Date().toISOString().slice(0, 10);

    component.onFormSubmit(
      formValue({ startPeriod: null, endPeriod: null, startYear: CURRENT_YEAR }),
    );

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.dateRange).toEqual({
      startDate: `${CURRENT_YEAR}-01-01`,
      endDate: today,
    });
  });

  it('maps filters to id arrays and nulls', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(
      formValue({
        filters: {
          countries: [COUNTRY_CM, COUNTRY_FR],
          departments: [DEPT_ENG],
          roles: [ROLE_SWE],
          employmentTypes: ['FULL_TIME', 'PART_TIME'],
        },
      }),
    );

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.anyCountryIds).toEqual([COUNTRY_CM.id, COUNTRY_FR.id]);
    expect(body.anyDepartmentIds).toEqual([DEPT_ENG.id]);
    expect(body.anyRoleIds).toEqual([ROLE_SWE.id]);
    expect(body.anyEmploymentTypes).toEqual(['FULL_TIME', 'PART_TIME']);
  });

  it('sets includeStatistics = false for non-TOTAL kinds', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody(['COUNTRY'])));

    component.onKindChange('COUNTRY');   // no prior submit → no fetch
    component.onFormSubmit(formValue()); // fetch uses CURRENT kind

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.includeBreakdown).toBeTrue();
    expect(body.includeStatistics).toBeFalse();
  });

  // -----------------------------------------------------------------
  // isLoading
  // -----------------------------------------------------------------

  it('reports loading while the request is in flight', () => {
    const subj = new Subject<AggregateReportResponse>();
    reports.generateAggregateReport.and.returnValue(subj.asObservable());

    component.onFormSubmit(formValue());
    expect(component.isLoading()).toBeTrue();

    subj.next(successBody([]));
    subj.complete();
    expect(component.isLoading()).toBeFalse();
    expect(component.state()).toBe('success');
  });

  // -----------------------------------------------------------------
  // kind toggle — cache hit
  // -----------------------------------------------------------------

  it('uses cached response on repeat toggle without a new request', () => {
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: [] }))
      .and.returnValue(of(successBody([])));
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: ['COUNTRY'] }))
      .and.returnValue(of(successBody(['COUNTRY'])));

    component.onFormSubmit(formValue());
    expect(component.state()).toBe('success');

    component.onKindChange('COUNTRY');
    expect(component.reportResponse()!.groupKeys).toEqual(['COUNTRY']);
    expect(reports.generateAggregateReport).toHaveBeenCalledTimes(2);

    component.onKindChange('TOTAL');
    // no new call — cached
    expect(reports.generateAggregateReport).toHaveBeenCalledTimes(2);
    expect(component.reportResponse()!.groupKeys).toEqual([]);
  });

  // -----------------------------------------------------------------
  // kind toggle — cache miss with prior submit
  // -----------------------------------------------------------------

  it('auto-regenerates on toggle when a report was already generated', () => {
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: [] }))
      .and.returnValue(of(successBody([])));
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: ['DEPARTMENT'] }))
      .and.returnValue(of(successBody(['DEPARTMENT'])));

    component.onFormSubmit(formValue());

    component.onKindChange('DEPARTMENT');

    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.groupKeys).toEqual(['DEPARTMENT']);
    expect(component.selectedKind()).toBe('DEPARTMENT');
    expect(component.reportResponse()!.groupKeys).toEqual(['DEPARTMENT']);
  });

  // -----------------------------------------------------------------
  // kind toggle — no prior submit
  // -----------------------------------------------------------------

  it('does nothing on toggle when no report was ever generated', () => {
    component.onKindChange('COUNTRY');

    expect(reports.generateAggregateReport).not.toHaveBeenCalled();
    expect(component.selectedKind()).toBe('COUNTRY');
    expect(component.state()).toBe('idle');
    expect(component.reportResponse()).toBeNull();
  });

  // -----------------------------------------------------------------
  // kind toggle — same kind
  // -----------------------------------------------------------------

  it('does nothing on toggle when selecting the already-selected kind', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(formValue());
    const callsAfterSubmit = reports.generateAggregateReport.calls.count();

    component.onKindChange('TOTAL');
    expect(reports.generateAggregateReport.calls.count()).toBe(callsAfterSubmit);
    expect(component.selectedKind()).toBe('TOTAL');
  });

  // -----------------------------------------------------------------
  // blocker banner
  // -----------------------------------------------------------------

  it('shows blocker content on REPORT_BLOCKED and toasts', () => {
    reports.generateAggregateReport.and.returnValue(
      throwError(() => makeReportsError('REPORT_BLOCKED')),
    );

    component.onFormSubmit(formValue());
    fixture.detectChanges();

    expect(component.state()).toBe('error');
    expect(component.blockers().length).toBe(1);
    expect(blockerBannerEl(fixture)).toBeTruthy();
    expect(component.warnings().length).toBe(0);
    expect(toast.error).toHaveBeenCalled();
  });

  it('dismissBanner clears error and blocker content', () => {
    reports.generateAggregateReport.and.returnValue(
      throwError(() => makeReportsError('REPORT_BLOCKED')),
    );

    component.onFormSubmit(formValue());
    fixture.detectChanges();

    component.dismissBanner();
    fixture.detectChanges();

    expect(component.state()).toBe('idle');
    expect(component.blockers().length).toBe(0);
    expect(component.reportError()).toBeNull();
  });

  // -----------------------------------------------------------------
  // warning banner
  // -----------------------------------------------------------------

  it('exposes warnings when the response carries them', () => {
    const warnings = [
      {
        code: 'MISSING_PAYROLL_SUMMARY',
        message: '3 employees have no payroll summary',
        severity: 'WARNING',
      },
    ];
    reports.generateAggregateReport.and.returnValue(
      of(successBody([], warnings)),
    );

    component.onFormSubmit(formValue());
    fixture.detectChanges();

    expect(component.state()).toBe('success');
    expect(component.warnings().length).toBe(1);
    expect(component.blockers().length).toBe(0);
    expect(warningBannerEl(fixture)).toBeTruthy();
  });

  it('has no warnings when the response carries none', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(formValue());
    fixture.detectChanges();

    expect(component.warnings().length).toBe(0);
    expect(component.blockers().length).toBe(0);
  });

  // -----------------------------------------------------------------
  // onFormChange resets state
  // -----------------------------------------------------------------

  it('onFormChange clears response, error, cache, state and selected kind', () => {
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: [] }))
      .and.returnValue(of(successBody([])));
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: ['COUNTRY'] }))
      .and.returnValue(of(successBody(['COUNTRY'])));

    component.onFormSubmit(formValue());
    component.onKindChange('COUNTRY');
    expect(component.reportResponse()).not.toBeNull();
    expect(component.state()).toBe('success');
    expect(component.selectedKind()).toBe('COUNTRY');

    component.onFormChange();
    fixture.detectChanges();

    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.showResults()).toBeFalse();
    expect(component.blockers().length).toBe(0);
    expect(component.warnings().length).toBe(0);
  });

  it('onFormChange invalidates the cache so a subsequent toggle fetches again', () => {
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: [] }))
      .and.returnValue(of(successBody([])));
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: ['COUNTRY'] }))
      .and.returnValue(of(successBody(['COUNTRY'])));

    component.onFormSubmit(formValue());
    component.onKindChange('COUNTRY');

    component.onFormChange();

    component.onKindChange('TOTAL');
    // No prior submit and no cache → no fetch.
    expect(component.reportResponse()).toBeNull();

    component.onFormSubmit(formValue());
    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.groupKeys).toEqual([]);
    expect(component.state()).toBe('success');
  });

  it('onFormChange after a blocker clears the blocker content', () => {
    reports.generateAggregateReport.and.returnValue(
      throwError(() => makeReportsError('REPORT_BLOCKED')),
    );

    component.onFormSubmit(formValue());
    fixture.detectChanges();
    expect(component.blockers().length).toBe(1);

    component.onFormChange();
    fixture.detectChanges();

    expect(component.blockers().length).toBe(0);
    expect(component.state()).toBe('idle');
  });

  // -----------------------------------------------------------------
  // onFormReset delegates to onFormChange
  // -----------------------------------------------------------------

  it('onFormReset clears cache, results and returns to TOTAL', () => {
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: [] }))
      .and.returnValue(of(successBody([])));
    reports.generateAggregateReport
      .withArgs(jasmine.objectContaining({ groupKeys: ['COUNTRY'] }))
      .and.returnValue(of(successBody(['COUNTRY'])));

    component.onFormSubmit(formValue());
    component.onKindChange('COUNTRY');
    expect(component.reportResponse()).not.toBeNull();

    component.onFormReset();
    fixture.detectChanges();

    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.reportResponse()).toBeNull();
    expect(component.reportError()).toBeNull();
    expect(component.showResults()).toBeFalse();
  });

  it('onFormReset leaves the page ready for a fresh submit', () => {
    reports.generateAggregateReport.and.returnValue(of(successBody([])));

    component.onFormSubmit(formValue());
    component.onFormReset();

    component.onFormSubmit(formValue());
    const body = reports.generateAggregateReport.calls.mostRecent()
      .args[0] as AggregateReportRequest;
    expect(body.groupKeys).toEqual([]);
    expect(component.state()).toBe('success');
  });
});
