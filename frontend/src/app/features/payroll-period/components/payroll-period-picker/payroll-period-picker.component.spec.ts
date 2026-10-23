import { ComponentFixture, TestBed } from '@angular/core/testing';
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
});
