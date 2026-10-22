import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  AggregateReportFormValue,
} from '../../components/aggregate-report-form/aggregate-report-form.component';
import { AggregateReportRequest } from '../../models';
import { ToastService } from '../../../../core/ui/toast/toast.service';
import { AggregateReportPageComponent } from './aggregate-report.page';

const URL = '/api/v1/reports/aggregate';

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

const formValue = (overrides: Partial<AggregateReportFormValue> = {}): AggregateReportFormValue => ({
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
    lines: [{ amount: { amount: 1000, currency: 'USD' }, employeeCount: 2, country: 'CM' }],
    warnings: [],
  };
}

function blockedBody() {
  return {
    code: 'REPORT_BLOCKED',
    message: 'Report could not be generated',
    errors: [{
      field: 'employees[1].currency',
      code: 'MISSING_CURRENCY',
      message: 'Employee has no currency',
      attributes: {},
    }],
    timestamp: '2026-10-19T12:00:00Z',
  };
}

describe('AggregateReportPageComponent', () => {
  let fixture: ComponentFixture<AggregateReportPageComponent>;
  let component: AggregateReportPageComponent;
  let http: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success', 'error', 'info', 'warning', 'dismiss',
    ]);

    await TestBed.configureTestingModule({
      imports: [AggregateReportPageComponent, NoopAnimationsModule],
      providers: [
        { provide: ToastService, useValue: toast },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportPageComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('starts idle, TOTAL tab active, no results', () => {
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.showResults()).toBeFalse();
  });

  it('submits form and shows results for TOTAL', () => {
    component.onFormSubmit(formValue());

    const req = http.expectOne(URL);
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

    const req = http.expectOne(URL);
    expect(req.request.body.dateRange).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-09-30',
    });
    req.flush(successBody([]));
  });

  it('uses cached response on repeat toggle without new request', () => {
    // Fetch TOTAL
    component.onFormSubmit(formValue());
    http.expectOne(URL).flush(successBody([]));
    expect(component.state()).toBe('success');

    // Switch to COUNTRY → new request
    component.onKindChange('COUNTRY');
    http.expectOne(URL).flush(successBody(['COUNTRY']));
    expect(component.reportResponse()!.groupKeys).toEqual(['COUNTRY']);

    // Switch back to TOTAL → cached, no HTTP request
    component.onKindChange('TOTAL');
    http.expectNone(URL);
    expect(component.reportResponse()!.groupKeys).toEqual([]);
  });

  it('auto-regenerates on toggle when a report was already generated', () => {
    component.onFormSubmit(formValue());
    http.expectOne(URL).flush(successBody([]));

    component.onKindChange('DEPARTMENT');
    const req = http.expectOne(URL);
    expect(req.request.body.groupKeys).toEqual(['DEPARTMENT']);
    req.flush(successBody(['DEPARTMENT']));

    expect(component.selectedKind()).toBe('DEPARTMENT');
    expect(component.reportResponse()!.groupKeys).toEqual(['DEPARTMENT']);
  });

  it('does nothing on toggle when no report was ever generated', () => {
    component.onKindChange('COUNTRY');
    http.expectNone(URL);
    expect(component.selectedKind()).toBe('COUNTRY');
    expect(component.state()).toBe('idle');
    expect(component.reportResponse()).toBeNull();
  });

  it('shows blocker banner on REPORT_BLOCKED and toasts', () => {
    component.onFormSubmit(formValue());
    http.expectOne(URL).flush(blockedBody(), {
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
    http.expectOne(URL).flush(blockedBody(), {
      status: 400,
      statusText: 'Bad Request',
    });
    component.dismissBanner();
    expect(component.state()).toBe('idle');
    expect(component.showBlockerBanner()).toBeFalse();
  });

  it('onFormReset clears cache, results and returns to TOTAL', () => {
    component.onFormSubmit(formValue());
    http.expectOne(URL).flush(successBody([]));
    component.onKindChange('COUNTRY');
    http.expectOne(URL).flush(successBody(['COUNTRY']));
    expect(component.hasEverGenerated()).toBeTrue();

    component.onFormReset();
    expect(component.state()).toBe('idle');
    expect(component.selectedKind()).toBe('TOTAL');
    expect(component.reportResponse()).toBeNull();
    expect(component.hasEverGenerated()).toBeFalse();
  });
});
