import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakdownDonutComponent } from './breakdown-donut.component';
import { AggregateLineDto } from '../../models';

describe('BreakdownDonutComponent', () => {
  let component: BreakdownDonutComponent;
  let fixture: ComponentFixture<BreakdownDonutComponent>;

  const mockLine: AggregateLineDto = {
    amount: { currency: 'USD', amount: 2000 },
    employeeCount: 10,
    breakdown: {
      base: 1200,
      variable: 300,
      allowances: 300,
      employerTaxes: 200,
      total: 2000,
      currency: 'USD',
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BreakdownDonutComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BreakdownDonutComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('line', mockLine);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute correct breakdown total and generate slices', () => {
    expect(component.hasBreakdown()).toBeTrue();
    expect(component.total()).toBe(2000);
    expect(component.slices().length).toBe(4);
    expect(component.slices()[0].value).toBe(1200);
    expect(component.slices()[0].percent).toBe(60);
  });
});
