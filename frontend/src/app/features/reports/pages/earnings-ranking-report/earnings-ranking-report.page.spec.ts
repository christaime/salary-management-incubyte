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
import { EarningsRankingReportFormComponent } from '../../components/earnings-ranking-report-form/earnings-ranking-report-form.component';
import { PositionContextComponent } from '../../components/position-context/position-context.component';
import { EarnerSpectrumComponent } from '../../components/earner-spectrum/earner-spectrum.component';
import { apiUrl } from '@core/config/api-base-url';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { httpErrorInterceptor } from '@core/http/http-error.interceptor';

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
      {
        code: 'PERIOD_CLOSED',
        message,
        severity: 'BLOCKER',
      } as any,
    ],
  };
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

const emptyRequest: EarningRankingReportRequest = {
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
        { provide: ReportsService, useValue: reportsService }
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

  it('renders the title and empty state on idle', () => {
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
  // Success path
  // ------------------------------------------------------------------

  it('calls generateRankingReport with the emitted request', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    expect(reportsService.generateRankingReport).toHaveBeenCalledOnceWith(
      emptyRequest,
    );
    expect(component.state()).toBe('success');
  });

  it('renders position context, earner spectrum and metadata on success', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-position-context')).not.toBeNull();
    expect(el.querySelector('app-earner-spectrum')).not.toBeNull();
    expect(el.textContent).toContain('January 2026');
    expect(el.textContent).toContain('USD');
    expect(el.textContent).toContain('GLOBAL');
    expect(el.textContent).toContain('10 employees in scope');
  });

  it('does not show blocker or warning wrappers on clean success', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
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
    const response = makeResponse({
      tops: [makeEarner(1, 10_000, 9999), makeEarner(2, 5_000, 9900)],
      bottoms: [makeEarner(999, 1_000, 10), makeEarner(1000, 500, 1)],
    });
    reportsService.generateRankingReport.and.returnValue(of(response));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    // (10_000 + 5_000) × 12 = 180_000
    expect(component.topTotalAnnual()).toBe(180_000);
    // (1_000 + 500) × 12 = 18_000
    expect(component.bottomTotalAnnual()).toBe(18_000);
  });

  it('returns zero totals when the report is null', () => {
    expect(component.topTotalAnnual()).toBe(0);
    expect(component.bottomTotalAnnual()).toBe(0);
  });

  // ------------------------------------------------------------------
  // Form wiring
  // ------------------------------------------------------------------

  it('forwards the form submit through the child form component', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));
    fixture.detectChanges();

    const formDE = fixture.debugElement.query(
      By.directive(EarningsRankingReportFormComponent),
    );
    expect(formDE).toBeTruthy();

    formDE.componentInstance.submitForm.emit(emptyRequest);
    fixture.detectChanges();

    expect(reportsService.generateRankingReport).toHaveBeenCalledWith(
      emptyRequest,
    );
  });

  it('forwards the form cancel through the child form component', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    // First get into success state
    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
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

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    const ctxDE = fixture.debugElement.query(
      By.directive(PositionContextComponent),
    );
    expect(ctxDE).toBeTruthy();

    const ctx = ctxDE.componentInstance as PositionContextComponent;
    expect(ctx.data()).toEqual(response.positionContext);
    expect(ctx.referenceCurrency()).toBe('USD');
  });

  it('renders the "no earners in scope" zone when the response is empty', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeEmptyResponse()));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="ranking-empty-state"]')).not.toBeNull();
    expect(el.textContent).toContain('No earners in scope');
    // No full-report sections rendered
    expect(el.querySelector('app-position-context')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).toBeNull();
  });

  it('renders the idle zone before any submission', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="ranking-empty-state"]')).toBeNull();
    expect(el.textContent).toContain('Pick a payroll period');
  });

  it('does not render the empty state zone on success with data', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeResponse()));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="ranking-empty-state"]')).toBeNull();
    expect(el.querySelector('app-earner-spectrum')).not.toBeNull();
  });

  it('exposes hasData as false for an empty response', () => {
    reportsService.generateRankingReport.and.returnValue(of(makeEmptyResponse()));

    component.onSubmit(emptyRequest);
    fixture.detectChanges();

    expect(component.hasData()).toBeFalse();
  });
});
