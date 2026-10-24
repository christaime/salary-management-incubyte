import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { PayrollPeriodPickerComponent } from './payroll-period-picker.component';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodDto } from '../../models';

describe('PayrollPeriodPickerComponent', () => {
  let component: PayrollPeriodPickerComponent;
  let fixture: ComponentFixture<PayrollPeriodPickerComponent>;

  const mockPeriods: PayrollPeriodDto[] = [
    {
      id: 'p1',
      name: 'January 2026',
      frequency: 'MONTHLY',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      closed: true,
    },
    {
      id: 'p2',
      name: 'February 2026',
      frequency: 'MONTHLY',
      startDate: '2026-02-01',
      endDate: '2026-02-28',
      closed: false,
    },
  ];

  beforeEach(async () => {
    const spy = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spy.search.and.returnValue(of({ content: mockPeriods, totalElements: 2, totalPages: 1, size: 20, number: 0 }));

    await TestBed.configureTestingModule({
      imports: [PayrollPeriodPickerComponent],
      providers: [
        provideAnimations(),
        { provide: PayrollPeriodsService, useValue: spy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should write value correctly via ControlValueAccessor and set internal year', () => {
    const period = mockPeriods[0];
    component.writeValue(period);
    expect(component.activeSelectedPeriod()).toEqual(period);
    expect(component.internalYear()).toBe(2026);
  });

  it('should select a period, update form value via onChange, and emit periodChange', () => {
    const onChangeSpy = jasmine.createSpy('onChange');
    const onTouchedSpy = jasmine.createSpy('onTouched');
    spyOn(component.periodChange, 'emit');

    component.registerOnChange(onChangeSpy);
    component.registerOnTouched(onTouchedSpy);

    const period = mockPeriods[1];
    component.selectPeriod(period);

    expect(component.activeSelectedPeriod()).toEqual(period);
    expect(onChangeSpy).toHaveBeenCalledWith(period);
    expect(onTouchedSpy).toHaveBeenCalled();
    expect(component.periodChange.emit).toHaveBeenCalledWith({
      year: 2026,
      period,
    });
  });

  it('should clear selected period and notify onChange', () => {
    const onChangeSpy = jasmine.createSpy('onChange');
    spyOn(component.cleared, 'emit');

    component.registerOnChange(onChangeSpy);
    component.clearSelectedPeriod();

    expect(component.activeSelectedPeriod()).toBeNull();
    expect(onChangeSpy).toHaveBeenCalledWith(null);
    expect(component.cleared.emit).toHaveBeenCalled();
  });

  it('should re-query when initialFilter changes', fakeAsync(() => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    const service = TestBed.inject(PayrollPeriodsService) as jasmine.SpyObj<PayrollPeriodsService>;
    localFixture.detectChanges();
    tick(300);
    service.search.calls.reset();

    localFixture.componentRef.setInput('selectedYear', 2026);
    localFixture.componentRef.setInput('initialFilter', { startDate: '2026-01-31' });
    localFixture.detectChanges();
    tick();
    expect(service.search).toHaveBeenCalledTimes(2);// once for year selected, another for initial filter
    const secondCall = service.search.calls.mostRecent().args[0]!;
    expect(secondCall.startDate).toBe('2026-01-31');
    expect(secondCall.endDate).toBe('2026-12-31');
  }));

  it('should narrow the year range from initialFilter.startDate when internalYear is null', fakeAsync(() => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    const localComponent = localFixture.componentInstance;
    const service = TestBed.inject(PayrollPeriodsService) as jasmine.SpyObj<PayrollPeriodsService>;
    localFixture.detectChanges();
    tick(300);

    // Clear the year so internalYear = null, then change the filter
    localComponent.clearYearRange();
    localFixture.detectChanges();
    tick();
    service.search.calls.reset();

    localFixture.componentRef.setInput('initialFilter', { startDate: '2026-06-15' });
    localFixture.detectChanges();
    tick();

    expect(service.search).toHaveBeenCalled();
    const call = service.search.calls.mostRecent().args[0]!;
    expect(call.startDate).toBe('2026-06-15');
  }));

  it('required = null never shows the error', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.componentRef.setInput('required', null);
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.touched.set(true);
    localFixture.detectChanges();

    expect(cmp.isInvalid()).toBeFalse();
    expect(cmp.requiredMessage()).toBe('');
  });

  it('required = "year" is invalid when no year is set', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.componentRef.setInput('required', 'year');
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.touched.set(true);
    localFixture.detectChanges();

    expect(cmp.isInvalid()).toBeTrue();
    expect(cmp.requiredMessage()).toBe('Payroll year is required.');
  });

  it('required = "year" is valid once a year is set', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.componentRef.setInput('required', 'year');
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.touched.set(true);
    cmp.internalYear.set(2026);
    localFixture.detectChanges();

    expect(cmp.isInvalid()).toBeFalse();
  });

  it('required = "period" is invalid when no period is selected', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.componentRef.setInput('required', 'period');
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.touched.set(true);
    localFixture.detectChanges();

    expect(cmp.isInvalid()).toBeTrue();
    expect(cmp.requiredMessage()).toBe('Payroll period is required.');
  });

  it('required = "period" is valid once a period is selected', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.componentRef.setInput('required', 'period');
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.touched.set(true);
    cmp.selectPeriod(mockPeriods[0]);
    localFixture.detectChanges();

    expect(cmp.isInvalid()).toBeFalse();
  });

  it('selecting a period without a year sets internalYear and emits yearChange', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    spyOn(cmp.yearChange, 'emit');

    expect(cmp.internalYear()).toBeNull();
    cmp.selectPeriod(mockPeriods[0]);   // January 2026

    expect(cmp.internalYear()).toBe(2026);
    expect(cmp.yearChange.emit).toHaveBeenCalledWith(2026);
  });

  it('selecting a period when a year is already set does not emit yearChange', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.internalYear.set(2026);
    spyOn(cmp.yearChange, 'emit');

    cmp.selectPeriod(mockPeriods[0]);   // January 2026

    expect(cmp.internalYear()).toBe(2026);
    expect(cmp.yearChange.emit).not.toHaveBeenCalled();
  });

  it('selecting a period when a different year is set updates internalYear silently', () => {
    const localFixture = TestBed.createComponent(PayrollPeriodPickerComponent);
    localFixture.detectChanges();

    const cmp = localFixture.componentInstance;
    cmp.internalYear.set(2025);
    spyOn(cmp.yearChange, 'emit');

    cmp.selectPeriod(mockPeriods[0]);   // January 2026

    expect(cmp.internalYear()).toBe(2026);
    expect(cmp.yearChange.emit).not.toHaveBeenCalled();
  });
});
