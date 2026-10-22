import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AggregateLineDto, AggregateReportResponse } from '../../models';
import { TieredBarListComponent } from './tiered-bar-list.component';

function line(partial: Partial<AggregateLineDto>): AggregateLineDto {
  return {
    amount: { amount: 1000, currency: 'USD' },
    employeeCount: 10,
    ...partial,
  };
}

function response(lines: AggregateLineDto[], groupKeys: AggregateReportResponse['groupKeys'] = ['COUNTRY']): AggregateReportResponse {
  return {
    kind: 'AGGREGATE',
    dateRange: { startDate: '2026-01-01', endDate: '2026-03-31' },
    groupKeys,
    referenceCurrency: 'USD',
    lines,
    warnings: [],
  };
}

describe('TieredBarListComponent', () => {
  let fixture: ComponentFixture<TieredBarListComponent>;
  let component: TieredBarListComponent;

  function create(res: AggregateReportResponse): void {
    TestBed.configureTestingModule({
      imports: [TieredBarListComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(TieredBarListComponent);
    fixture.componentRef.setInput('response', res);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('builds rows sorted by amount descending', () => {
    create(
      response([
        line({ country: 'CM', amount: { amount: 500, currency: 'USD' } }),
        line({ country: 'US', amount: { amount: 2000, currency: 'USD' } }),
        line({ country: 'FR', amount: { amount: 1000, currency: 'USD' } }),
      ]),
    );
    expect(component.rows().map((r) => r.code)).toEqual(['US', 'FR', 'CM']);
  });

  it('computes barPct relative to the max amount', () => {
    create(
      response([
        line({ country: 'US', amount: { amount: 1000, currency: 'USD' } }),
        line({ country: 'FR', amount: { amount: 500, currency: 'USD' } }),
      ]),
    );
    const rows = component.rows();
    expect(rows[0].barPct).toBe(100);
    expect(rows[1].barPct).toBe(50);
  });

  it('emits rowClick when a row is clicked', () => {
    create(
      response([
        line({ country: 'US', amount: { amount: 1000, currency: 'USD' } }),
      ]),
    );
    let clicked: AggregateLineDto | null = null;
    component.rowClick.subscribe((l) => (clicked = l));
    component.onRowClick(component.rows()[0]);
    expect(clicked).not.toBeNull();
    expect(clicked!.country).toBe('US');
  });
});
