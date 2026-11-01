import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';

import { EarningsRankingReportPage } from './earnings-ranking-report.page';
import { ReportsService } from '../../services/reports.service';
import {
  EarningRankingReportRequest,
  EarningRankingReportResponse,
  EarnerRankingDto,
  PositionContextDto,
} from '../../models';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';
import { ReportsError } from '../../services/reports-error';
import { EarningsRankingReportFormComponent, EarningsRankingFormValue } from '../../components/earnings-ranking-report-form/earnings-ranking-report-form.component';
import { PositionContextComponent } from '../../components/position-context/position-context.component';
import { EarnerSpectrumComponent } from '../../components/earner-spectrum/earner-spectrum.component';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';
import { RefDto } from '@core/models';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makePeriod(overrides: Partial<PayrollPeriodDto> = {}): PayrollPeriodDto {
  return {
    id: 'p-1',
    name: 'January 2026',
    startDate: '2026-01-01',
    endDate: '2026-01-31',
    frequency: 'MONTHLY',
    closed: false,
    ...overrides,
  };
}

function makeFormValue(
  overrides: Partial<EarningsRankingFormValue> = {},
): EarningsRankingFormValue {
  return {
    period: makePeriod(),
    groupKey: null,
    country: null,
    department: null,
    role: null,
    employmentType: null,
    ...overrides,
  };
}

function makeEarner(
  rank: number,
  monthly: number,
  percentile: number | null,
): EarnerRankingDto {
  return {
    monthlyReferenceAmount: monthly,
    frequency: 'MONTHLY',
    percentile,
    rank,
    employee: {
      id: `e-${rank}`,
      matricule: `EMP-${rank}`,
      name: `Employee ${rank}`,
    },
    role: { id: 'r', code: 'DEV', name: 'Developer' },
    department: { id: 'd', code: 'ENG', name: 'Engineering' },
    country: { id: 'c', code: 'US', name: 'United States' },
    employmentType: 'FULL_TIME',
    period: makePeriod(),
  };
}

function makePositionContext(): PositionContextDto {
  return {
    employeeCount: 10,
    min: 33_600,
    p10: 33_600,
    p25: 45_600,
    p50: 66_000,
    p75: 108_000,
    p90: 126_000,
    max: 144_000,
  };
}

function makeResponse(
  overrides: Partial<EarningRankingReportResponse> = {},
): EarningRankingReportResponse {
  return {
    kind: 'RANKING',
    period: makePeriod(),
    referenceCurrency: 'USD',
    groupKey: null,
    tops: [makeEarner(1, 12_000, 9999), makeEarner(2, 10_000, 9900)],
    bottoms: [makeEarner(999, 1_500, 10), makeEarner(1000, 1_200, 1)],
    positionContext: makePositionContext(),
    totalReferenceAmount: 10_000_000,
    totalDimensionReferenceAmount: 10_000_000,
    totalMonthlyPeriod: 1,
    totalBiWeeklyPeriod: 0,
    ...overrides,
  };
}

function makeError(code: string, message: string): ReportsError {
  return {
    code,
    message,
    warnings: [
      { code: 'PERIOD_CLOSED', message, severity: 'BLOCKER' } as any,
    ],
  } as any;
}

function makeEmptyResponse(): EarningRankingReportResponse {
  return {
    kind: 'RANKING',
    period: makePeriod(),
    referenceCurrency: 'USD',
    groupKey: 'EMPLOYMENT_TYPE',
    tops: [],
    bottoms: [],
    positionContext: null,
    totalReferenceAmount: 111_674_309.89,
    totalDimensionReferenceAmount: 7_980_688.97,
    totalMonthlyPeriod: 1,
    totalBiWeeklyPeriod: 0,
  };
}

const COUNTRY_FR: RefDto = { id: 'c-fr', code: 'FR', name: 'France' };
const DEPT_ENG: RefDto = { id: 'd-eng', code: 'ENG', name: 'Engineering' };
const ROLE_SWE: RefDto = { id: 'r-swe', code: 'SWE', name: 'Software Engineer' };

/**
 * The request the page is expected to hand to ReportsService when
 * the form value has no groupKey (GLOBAL view).
 */
const globalRequest: EarningRankingReportRequest = {
  periodId: 'p-1',
  groupKey: null,
  countryId: null,
  departmentId: null,
  roleId: null,
  employmentType: null,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('EarningsRankingReportPage', () => {
  let fixture: ComponentFixture<EarningsRankingReportPage>;
  let component: EarningsRankingReportPage;
  let reportsService: jasmine.SpyObj<ReportsService>;

  beforeEach(async () => {
    reportsService = jasmine.createSpyObj('ReportsService', [
      'generateRankingReport',
    ]);

    await TestBed.configureTestingModule({
      imports: [EarningsRankingReportPage],
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        { provide: ReportsService, useValue: reportsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EarningsRankingReportPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ------------------------------------------------------------------
  // Idle state
  // ------------------------------------------------------------------

  it('renders the title and idle hint on idle', () => {
    const el = fixture.nativeElement as HTMLElement;

    expect(
      el.querySelector('[data-testid="earnings-ranking-title"]')?.textContent,
    ).toContain('Earnings ranking report');

    expect(el.textContent).toContain('Pick a payroll period');
    expect(el.querySelector('app-position-context')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).toBeNull();
    expect(el.querySelector('[data-testid="blocker-banner-wrapper"]')).toBeNull();
    expect(el.querySelector('[data-testid="warning-banner-wrapper"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Submit → request mapping
  // ------------------------------------------------------------------

  it('maps a GLOBAL form value to a request with all dimension ids null', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith(
      globalRequest,
    );
    expect(component.state()).toBe('success');
  });

  it('maps a COUNTRY form value to a request with only countryId set', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(
      makeFormValue({ groupKey: 'COUNTRY', country: COUNTRY_FR }),
    );

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith({
      periodId: 'p-1',
      groupKey: 'COUNTRY',
      countryId: COUNTRY_FR.id,
      departmentId: null,
      roleId: null,
      employmentType: null,
    });
  });

  it('maps a DEPARTMENT form value to a request with only departmentId set', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(
      makeFormValue({ groupKey: 'DEPARTMENT', department: DEPT_ENG }),
    );

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith({
      periodId: 'p-1',
      groupKey: 'DEPARTMENT',
      countryId: null,
      departmentId: DEPT_ENG.id,
      roleId: null,
      employmentType: null,
    });
  });

  it('maps a ROLE form value to a request with only roleId set', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(
      makeFormValue({ groupKey: 'ROLE', role: ROLE_SWE }),
    );

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith({
      periodId: 'p-1',
      groupKey: 'ROLE',
      countryId: null,
      departmentId: null,
      roleId: ROLE_SWE.id,
      employmentType: null,
    });
  });

  it('maps an EMPLOYMENT_TYPE form value to a request with only employmentType set', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(
      makeFormValue({ groupKey: 'EMPLOYMENT_TYPE', employmentType: 'FULL_TIME' }),
    );

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith({
      periodId: 'p-1',
      groupKey: 'EMPLOYMENT_TYPE',
      countryId: null,
      departmentId: null,
      roleId: null,
      employmentType: 'FULL_TIME',
    });
  });

  // ------------------------------------------------------------------
  // Success rendering
  // ------------------------------------------------------------------

  it('renders position context, earner spectrum and metadata on success', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-position-context')).not.toBeNull();
    expect(el.querySelector('app-earner-spectrum')).not.toBeNull();
    expect(el.textContent).toContain('January 2026');
    expect(el.textContent).toContain('USD');
  });

  it('does not show blocker or warning wrappers on clean success', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="blocker-banner-wrapper"]')).toBeNull();
    expect(el.querySelector('[data-testid="warning-banner-wrapper"]')).toBeNull();
  });

  it('renders the warning banner wrapper when the response has warnings', () => {
    const response = makeResponse({
      warnings: [
        {
          code: 'MISSING_PAYROLL_SUMMARY',
          message: 'Some employees have no payroll summary',
          severity: 'WARNING',
        },
      ],
    } as any);
    reportsService.generateRankingReport.and.returnValue(of(response));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="warning-banner-wrapper"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="blocker-banner-wrapper"]')).toBeNull();
    expect(el.textContent).toContain('Some employees have no payroll summary');
  });

  // ------------------------------------------------------------------
  // Error path
  // ------------------------------------------------------------------

  it('renders the blocker banner wrapper and hides the report on error', () => {
    reportsService.generateRankingReport.and.returnValue(
      throwError(() => makeError('REPORT_BLOCKED', 'Report could not be generated')),
    );

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="blocker-banner-wrapper"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="warning-banner-wrapper"]')).toBeNull();
    expect(el.querySelector('app-position-context')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).toBeNull();
    expect(component.state()).toBe('error');
    expect(component.error()?.code).toBe('REPORT_BLOCKED');
  });

  it('dismisses the error banner and returns to idle', () => {
    reportsService.generateRankingReport.and.returnValue(
      throwError(() => makeError('REPORT_BLOCKED', 'Blocked')),
    );

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();
    expect(component.state()).toBe('error');

    component.dismissBanner();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(component.state()).toBe('idle');
    expect(component.error()).toBeNull();
    expect(el.querySelector('[data-testid="blocker-banner-wrapper"]')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Cancel / reset
  // ------------------------------------------------------------------

  it('resets state and clears the report on cancel', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();
    expect(component.report()).not.toBeNull();

    component.onCancel();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(component.report()).toBeNull();
    expect(component.error()).toBeNull();
    expect(component.state()).toBe('idle');
    expect(el.querySelector('app-position-context')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).toBeNull();
  });

  // ------------------------------------------------------------------
  // Computed signals
  // ------------------------------------------------------------------

  it('computes topTotalAnnual and bottomTotalAnnual from the response', () => {
    reportsService.generateRankingReport.and.returnValue(
      of(
        makeResponse({
          tops: [makeEarner(1, 10_000, 9999), makeEarner(2, 5_000, 9900)],
          bottoms: [makeEarner(999, 1_000, 10), makeEarner(1000, 500, 1)],
        }),
      ),
    );

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    expect(component.topTotalAnnual()).toBe(180_000);     // (10_000 + 5_000) × 12
    expect(component.bottomTotalAnnual()).toBe(18_000);   // (1_000 + 500) × 12
  });

  it('returns zero totals when the report is null', () => {
    expect(component.topTotalAnnual()).toBe(0);
    expect(component.bottomTotalAnnual()).toBe(0);
  });

  // ------------------------------------------------------------------
  // Form wiring (child component)
  // ------------------------------------------------------------------

  it('forwards the child form submitForm output to ReportsService', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));
    fixture.detectChanges();

    const formDE = fixture.debugElement.query(
      By.directive(EarningsRankingReportFormComponent),
    );
    expect(formDE).toBeTruthy();

    formDE.componentInstance.submitForm.emit(makeFormValue());
    fixture.detectChanges();

    expect(reportsService.generateRankingReport).toHaveBeenCalledWith(globalRequest);
  });

  it('forwards the child form cancel output to reset state', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();
    expect(component.report()).not.toBeNull();

    const formDE = fixture.debugElement.query(
      By.directive(EarningsRankingReportFormComponent),
    );
    formDE.componentInstance.cancel.emit();
    fixture.detectChanges();

    expect(component.report()).toBeNull();
    expect(component.state()).toBe('idle');
  });

  // ------------------------------------------------------------------
  // Child input bindings
  // ------------------------------------------------------------------

  it('passes the correct inputs to the earner spectrum component', () => {
    const response = makeResponse();
    reportsService.generateRankingReport.and.returnValue(of(response));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const spectrumDE = fixture.debugElement.query(
      By.directive(EarnerSpectrumComponent),
    );
    expect(spectrumDE).toBeTruthy();

    const spectrum = spectrumDE.componentInstance as EarnerSpectrumComponent;
    expect(spectrum.tops()).toEqual(response.tops);
    expect(spectrum.bottoms()).toEqual(response.bottoms);
    expect(spectrum.referenceCurrency()).toBe('USD');
    expect(spectrum.frequency()).toBe('MONTHLY');
  });

  it('passes the correct inputs to the position context component', () => {
    const response = makeResponse();
    reportsService.generateRankingReport.and.returnValue(of(response));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const ctxDE = fixture.debugElement.query(
      By.directive(PositionContextComponent),
    );
    expect(ctxDE).toBeTruthy();

    const ctx = ctxDE.componentInstance as PositionContextComponent;
    expect(ctx.data()).toEqual(response.positionContext!);
    expect(ctx.referenceCurrency()).toBe('USD');
  });

  it('renders the "no earners in scope" zone when the response is empty', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeEmptyResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="ranking-empty-state"]')).not.toBeNull();
    expect(el.textContent).toContain('No earners in scope');
    expect(el.querySelector('app-position-context')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).toBeNull();
  });

  it('does not render the empty state zone on success with data', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="ranking-empty-state"]')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).not.toBeNull();
  });

  it('exposes hasData as false for an empty response', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeEmptyResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    expect(component.hasData()).toBeFalse();
  });

  it('exposes hasData as true for a non-empty response', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();

    expect(component.hasData()).toBeTrue();
  });

  // ------------------------------------------------------------------
  // onFormChange clears prior result
  // ------------------------------------------------------------------

  it('onFormChange clears the previous report and error', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmitForm(makeFormValue());
    fixture.detectChanges();
    expect(component.report()).not.toBeNull();

    component.onFormChange(makeFormValue({ groupKey: 'COUNTRY', country: COUNTRY_FR }));

    expect(component.report()).toBeNull();
    expect(component.error()).toBeNull();
    expect(component.state()).toBe('idle');
    expect(component.currentFormValue()?.groupKey).toBe('COUNTRY');
  });
});
