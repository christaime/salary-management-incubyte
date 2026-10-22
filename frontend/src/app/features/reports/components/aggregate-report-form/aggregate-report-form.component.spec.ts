import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { AggregateReportFormComponent, AggregateReportFormValue } from './aggregate-report-form.component';

const P_JAN = {
  id: '2026-01-M',
  name: 'January 2026',
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  frequency: 'MONTHLY' as const,
  closed: true,
};
const P_SEP = {
  id: '2026-09-M',
  name: 'September 2026',
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  frequency: 'MONTHLY' as const,
  closed: true,
};

describe('AggregateReportFormComponent', () => {
  let fixture: ComponentFixture<AggregateReportFormComponent>;
  let component: AggregateReportFormComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AggregateReportFormComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('cannot submit without a start period', () => {
    expect(component.canSubmit()).toBeFalse();
  });

  it('enables submit when start period is set', () => {
    component.form.controls.startPeriod.setValue(P_JAN);
    expect(component.canSubmit()).toBeTrue();
  });

  it('emits form value on submit', () => {
    component.form.controls.startPeriod.setValue(P_JAN);
    component.form.controls.countries.setValue(['CM']);

    let emitted: AggregateReportFormValue | null = null;
    component.submitForm.subscribe((v) => (emitted = v));
    component.onSubmit();

    expect(emitted).not.toBeNull();
    expect(emitted!.startPeriod.id).toBe('2026-01-M');
    expect(emitted!.endPeriod).toBeNull();
    expect(emitted!.filters.countryIds).toEqual(['CM']);
  });

  it('clears end period when start moves past it', () => {
    component.form.controls.startPeriod.setValue(P_JAN);
    component.form.controls.endPeriod.setValue(P_SEP);
    expect(component.endPeriod()?.id).toBe('2026-09-M');

    // Move start after end
    component.form.controls.startPeriod.setValue({
      ...P_SEP,
      id: '2026-10-M',
      name: 'October 2026',
      startDate: '2026-10-01',
      endDate: '2026-10-31',
    });
    expect(component.form.controls.endPeriod.value).toBeNull();
  });

  it('builds the end-picker initial filter from the start endDate', () => {
    component.form.controls.startPeriod.setValue(P_SEP);
    fixture.detectChanges();
    expect(component.endPeriodFilter()).toEqual({ startDate: '2026-09-30' });
  });
});
