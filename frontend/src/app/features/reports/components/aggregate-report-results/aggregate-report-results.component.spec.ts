import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { AggregateReportResultsComponent } from './aggregate-report-results.component';
import { AggregateReportResponse } from '../../models';

describe('AggregateReportResultsComponent', () => {
  let component: AggregateReportResultsComponent;
  let fixture: ComponentFixture<AggregateReportResultsComponent>;

  const mockResponse: AggregateReportResponse = {
    kind: 'AGGREGATE',
    referenceCurrency: 'USD',
    groupKeys: [],
    dateRange: {startDate: "2026-01-01", endDate:"2026-03-31"},
    lines: [
      {
        amount: { currency: 'USD', amount: 5000 },
        employeeCount: 25,
        breakdown: { base: 4000, variable: 500, allowances: 300, employerTaxes: 200, total: 5000, currency: 'USD' },
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AggregateReportResultsComponent],
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportResultsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('response', mockResponse);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute totals correctly for total report view', () => {
    expect(component.isEmpty()).toBeFalse();
    expect(component.isTotal()).toBeTrue();
    expect(component.totalAmount()).toBe(5000);
    expect(component.totalEmployees()).toBe(25);
    expect(component.currency()).toBe('USD');
  });
});
