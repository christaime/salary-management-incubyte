import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AggregateReportResponse } from '../../models';
import { AggregateReportResultsComponent } from './aggregate-report-results.component';

function makeResponse(overrides: Partial<AggregateReportResponse> = {}): AggregateReportResponse {
  return {
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-03-31' },
    groupKeys: ['COUNTRY'],
    referenceCurrency: 'USD',
    lines: [
      { amount: { amount: 1000, currency: 'USD' }, employeeCount: 2, country: 'CM' },
      { amount: { amount: 2000, currency: 'USD' }, employeeCount: 4, country: 'US' },
    ],
    warnings: [],
    ...overrides,
  };
}

describe('AggregateReportResultsComponent', () => {
  let fixture: ComponentFixture<AggregateReportResultsComponent>;
  let component: AggregateReportResultsComponent;

  function create(res: AggregateReportResponse): void {
    TestBed.configureTestingModule({
      imports: [AggregateReportResultsComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(AggregateReportResultsComponent);
    fixture.componentRef.setInput('response', res);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders the KPI card for the TOTAL view (groupKeys = [])', () => {
    create(makeResponse({
      groupKeys: [],
      lines: [{ amount: { amount: 5000, currency: 'USD' }, employeeCount: 10 }],
    }));
    expect(component.isTotal()).toBeTrue();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Total payroll cost');
    expect(text).toContain('5,000.00');
    expect(text).toContain('10 FTEs');
  });

  it('renders the two-column layout for grouped reports', () => {
    create(makeResponse());
    expect(component.isTotal()).toBeFalse();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-tiered-bar-list')).not.toBeNull();
    expect(el.querySelector('app-breakdown-donut')).not.toBeNull();
  });

  it('updates selectedLine when a row is clicked', () => {
    create(makeResponse());
    const line = makeResponse().lines[0];
    component.onRowClick(line);
    expect(component.selectedLine()).toBe(line);
  });

  it('shows empty state when lines is empty', () => {
    create(makeResponse({ lines: [] }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No data for this selection.');
  });

  it('shows warnings panel when warnings present', () => {
    create(makeResponse({
      warnings: [{ code: 'X', severity: 'WARNING', message: 'A problem' }],
    }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('A problem');
  });
});
