import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AggregateLineDto } from '../../models';
import { BreakdownDonutComponent } from './breakdown-donut.component';

function lineWithBreakdown(): AggregateLineDto {
  return {
    amount: { amount: 1400, currency: 'USD' },
    employeeCount: 10,
    country: 'US',
    breakdown: {
      base: 1000,
      variable: 200,
      allowances: 100,
      employerTaxes: 100,
      total: 1400,
      currency: 'USD',
    },
  };
}

describe('BreakdownDonutComponent', () => {
  let fixture: ComponentFixture<BreakdownDonutComponent>;
  let component: BreakdownDonutComponent;

  function create(line: AggregateLineDto | null): void {
    TestBed.configureTestingModule({ imports: [BreakdownDonutComponent] }).compileComponents();
    fixture = TestBed.createComponent(BreakdownDonutComponent);
    fixture.componentRef.setInput('line', line);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('shows placeholder when no line is provided', () => {
    create(null);
    expect(component.hasBreakdown()).toBeFalse();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Select a row');
  });

  it('computes slice percentages from the breakdown', () => {
    create(lineWithBreakdown());
    const slices = component.slices();
    expect(slices.length).toBe(4);
    const base = slices.find((s) => s.key === 'base')!;
    // 1000 / 1400 = 71.4%
    expect(Math.round(base.percent * 10) / 10).toBe(71.4);
  });

  it('filters out zero-value slices', () => {
    create({
      amount: { amount: 1000, currency: 'USD' },
      employeeCount: 5,
      country: 'FR',
      breakdown: {
        base: 1000,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 1000,
        currency: 'USD',
      },
    });
    expect(component.slices().length).toBe(1);
  });
});
