import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TieredBarListComponent } from './tiered-bar-list.component';
import { AggregateReportResponse } from '../../models';

describe('TieredBarListComponent', () => {
  let component: TieredBarListComponent;
  let fixture: ComponentFixture<TieredBarListComponent>;

  const mockResponse: AggregateReportResponse = {
    kind: 'AGGREGATE',
    referenceCurrency: 'USD',
    groupKeys: ['COUNTRY'],
    dateRange: {startDate: "2026-01-01", endDate:"2026-03-31"},
    lines: [
      {
        country: { code: 'US', name: 'United States' },
        amount: { currency: 'USD', amount: 3000 },
        breakdown: { base: 2000, variable: 500, allowances: 300, employerTaxes: 200, total: 3000, currency: 'USD' },
        employeeCount: 15,
        statistics: {} as any,
      },
      {
        country: { code: 'FR', name: 'France' },
        amount: { currency: 'USD', amount: 7000 },
        breakdown: { base: 4000, variable: 1500, allowances: 800, employerTaxes: 700, total: 7000, currency: 'USD' },
        employeeCount: 35,
        statistics: {} as any,
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TieredBarListComponent],
      providers: [provideAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(TieredBarListComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('response', mockResponse);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should sort rows descending by amount and mark the first as tier 1', () => {
    const rows = component.rows();
    expect(rows.length).toBe(2);
    // France (7000) should be first, US (3000) second
    expect(rows[0].code).toBe('FR');
    expect(rows[0].isTier1).toBeTrue();
    expect(rows[1].code).toBe('US');
    expect(rows[1].isTier1).toBeFalse();
  });

  it('should emit rowClick output when a row is clicked', () => {
    spyOn(component.rowClick, 'emit');
    const row = component.rows()[0];
    component.onRowClick(row);
    expect(component.rowClick.emit).toHaveBeenCalledWith(row.line);
  });
});
