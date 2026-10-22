import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { PayrollPeriodPickerComponent } from './payroll-period-picker.component';

describe('PayrollPeriodPickerComponent', () => {
  let fixture: ComponentFixture<PayrollPeriodPickerComponent>;
  let component: PayrollPeriodPickerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PayrollPeriodPickerComponent, NoopAnimationsModule, ReactiveFormsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('startAllowYear', 2025);
    fixture.detectChanges();
  });

  it('exposes years from startAllowYear to current year, DESC', () => {
    const years = component.years();
    expect(years[0]).toBe(new Date().getFullYear());
    expect(years[years.length - 1]).toBe(2025);
    // Strictly descending
    for (let i = 1; i < years.length; i++) {
      expect(years[i]).toBeLessThan(years[i - 1]);
    }
  });

  it('updates selectedYear when picking a year', () => {
    expect(component.selectedYear()).toBeNull();
    component.onSelectYear(2026);
    expect(component.selectedYear()).toBe(2026);
  });

  it('emits the selected period through the CVA', () => {
    const control = new FormControl<unknown>(null);
    let emitted: unknown = undefined;
    control.registerOnChange;
    component.registerOnChange((v) => (emitted = v));

    component.onSelectPeriod({
      id: '2026-09-M',
      name: 'September 2026',
      startDate: '2026-09-01',
      endDate: '2026-09-30',
      frequency: 'MONTHLY',
      closed: true,
    });

    expect(emitted).not.toBeUndefined();
    expect((emitted as { id: string }).id).toBe('2026-09-M');
  });

  it('reports required error when required and empty', () => {
    fixture.componentRef.setInput('isRequired', true);
    fixture.detectChanges();
    expect(component.validate({} as never)).toEqual({ required: true });

    component.onSelectPeriod({
      id: 'x',
      name: 'X',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: true,
    });
    expect(component.validate({} as never)).toBeNull();
  });
});
