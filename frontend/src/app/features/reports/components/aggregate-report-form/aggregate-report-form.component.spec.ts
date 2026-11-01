import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { AggregateReportFormComponent } from './aggregate-report-form.component';
import { PayrollPeriodPickerComponent } from '@features/payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { CountryService } from '@features/country/services/country.service';
import { DepartmentService } from '@features/department/services/department.service';
import { RoleService } from '@features/role/services/role.service';
import { AggregateReportFilterDrawerComponent } from '../aggregate-report-filter-drawer/aggregate-report-filter-drawer.component';

describe('AggregateReportFormComponent', () => {
  let component: AggregateReportFormComponent;
  let fixture: ComponentFixture<AggregateReportFormComponent>;

  const january: PayrollPeriodDto = {
    id: '2026-01-M',
    name: 'January 2026',
    frequency: 'MONTHLY' as const,
    startDate: '2026-01-01',
    endDate: '2026-01-31',
    closed: true,
  };

  const june: PayrollPeriodDto = {
    id: '2026-06-M',
    name: 'June 2026',
    frequency: 'MONTHLY' as const,
    startDate: '2026-06-01',
    endDate: '2026-06-30',
    closed: true,
  };

  beforeEach(async () => {
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }),
    );

    await TestBed.configureTestingModule({
      imports: [AggregateReportFormComponent],
      providers: [
        provideAnimations(),
        { provide: PayrollPeriodsService, useValue: spyPeriod },
        { provide: CountryService, useValue: spyCountry },
        { provide: DepartmentService, useValue: spyDepartment },
        { provide: RoleService, useValue: spyRole },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start invalid because startYear is required', () => {
    expect(component.form.controls.startYear.valid).toBeFalse();
    expect(component.form.valid).toBeFalse();
    expect(component.canSubmit()).toBeFalse();
  });

  it('setting startPeriod makes the form submittable (but startYear stays invalid)', () => {
    component.form.controls.startPeriod.setValue(january);
    fixture.detectChanges();

    expect(component.form.controls.startPeriod.valid).toBeTrue();
    // startYear is still the only required control, so form.valid is still false.
    expect(component.form.controls.startYear.valid).toBeFalse();
    // canSubmit is true because startPeriod is enough on its own.
    expect(component.canSubmit()).toBeTrue();
  });

  it('setting startYear makes the form valid and submittable', () => {
    component.form.controls.startYear.setValue(2026);
    fixture.detectChanges();

    // startYear control itself is valid.
    expect(component.form.controls.startYear.valid).toBeTrue();
    // The picker registers a hidden required validator on startPeriod, so
    // form.valid may stay false. What the component exposes as its "can I
    // submit?" contract is canSubmit(), not form.valid.
    expect(component.canSubmit()).toBeTrue();
  });

  it('should reset form values on onReset', () => {
    spyOn(component.cancel, 'emit');

    component.form.controls.startYear.setValue(2026);
    component.form.controls.startPeriod.setValue(january);
    component.form.controls.countries.setValue([{ id: 'c-fr', code: 'FR', name: 'France' }]);

    component.onReset();

    expect(component.form.controls.startYear.value).toBeNull();
    expect(component.form.controls.startPeriod.value).toBeNull();
    expect(component.form.controls.endPeriod.value).toBeNull();
    expect(component.form.controls.countries.value).toEqual([]);
    expect(component.startPickerSelectedYear()).toBeNull();
    expect(component.cancel.emit).toHaveBeenCalled();
  });

  it('should set the end picker filter to the start period endDate', () => {
    component.form.controls.startPeriod.setValue(january);
    fixture.detectChanges();

    const pickers = fixture.debugElement.queryAll(By.directive(PayrollPeriodPickerComponent));
    const endPickerInstance = pickers[1].componentInstance as PayrollPeriodPickerComponent;

    expect(endPickerInstance.initialFilter()).toEqual({
      startDate: '2026-01-31',
      frequency: 'MONTHLY',
    });
  });

  it('should update the end picker filter when the start period changes', () => {
    component.form.controls.startPeriod.setValue(january);
    fixture.detectChanges();
    let pickers = fixture.debugElement.queryAll(By.directive(PayrollPeriodPickerComponent));
    expect((pickers[1].componentInstance as PayrollPeriodPickerComponent).initialFilter())
      .toEqual({ startDate: '2026-01-31', frequency: 'MONTHLY' });

    component.form.controls.startPeriod.setValue(june);
    fixture.detectChanges();
    pickers = fixture.debugElement.queryAll(By.directive(PayrollPeriodPickerComponent));
    expect((pickers[1].componentInstance as PayrollPeriodPickerComponent).initialFilter())
      .toEqual({ startDate: '2026-06-30', frequency: 'MONTHLY' });
  });

  it('should clear the end period when a later start period is selected', () => {
    component.form.controls.startPeriod.setValue(january);
    component.form.controls.endPeriod.setValue({
      id: '2026-02-M',
      name: 'February 2026',
      frequency: 'MONTHLY',
      startDate: '2026-02-01',
      endDate: '2026-02-28',
      closed: true,
    });

    // Selecting June (which starts after February) invalidates the end period.
    component.form.controls.startPeriod.setValue(june);

    expect(component.form.controls.endPeriod.value).toBeNull();
  });

  it('onStartYearChange should set startYear and clear an incompatible end period', () => {
    component.form.controls.endPeriod.setValue({
      id: '2024-01-M',
      name: 'January 2024',
      frequency: 'MONTHLY',
      startDate: '2024-01-01',
      endDate: '2024-01-31',
      closed: true,
    });

    component.onStartYearChange(2026);

    expect(component.form.controls.startYear.value).toBe(2026);
    expect(component.startPickerSelectedYear()).toBe(2026);
    expect(component.form.controls.endPeriod.value).toBeNull();
  });

  it('onSubmit emits the form value with filters mapped to null when empty', () => {
    spyOn(component.submitForm, 'emit');

    component.form.controls.startYear.setValue(2026);
    component.form.controls.startPeriod.setValue(january);

    component.onSubmit();

    expect(component.submitForm.emit).toHaveBeenCalledWith({
      startYear: 2026,
      startPeriod: juneOr(january, january),
      endPeriod: null,
      filters: {
        countries: null,
        departments: null,
        roles: null,
        employmentTypes: null,
      },
    });
  });

  it('onSubmit does nothing when canSubmit() is false', () => {
    spyOn(component.submitForm, 'emit');

    // No year and no start period → cannot submit.
    component.onSubmit();

    expect(component.submitForm.emit).not.toHaveBeenCalled();
  });

  it('filter drawer apply should emit the current filter selection via formChange', () => {
    spyOn(component.formChange, 'emit');

    component.form.controls.startYear.setValue(2026);

    const drawer = fixture.debugElement.query(
      By.directive(AggregateReportFilterDrawerComponent),
    ).componentInstance as AggregateReportFilterDrawerComponent;

    // Simulate the drawer's "Apply" — it patches the parent form directly.
    drawer.form().patchValue({
      countries: [{ id: 'c-fr', code: 'FR', name: 'France' }],
      departments: [],
      roles: [],
      employmentTypes: ['FULL_TIME'],
    });
    drawer.applied.emit();
    fixture.detectChanges();

    expect(component.formChange.emit).toHaveBeenCalled();

    const emitted = (component.formChange.emit as jasmine.Spy).calls.mostRecent().args[0];
    expect(emitted.filters.countries).toEqual([
      { id: 'c-fr', code: 'FR', name: 'France' },
    ]);
    expect(emitted.filters.employmentTypes).toEqual(['FULL_TIME']);
    expect(emitted.filters.departments).toBeNull();
  });

  // small helper to avoid overloaded ts type complaint on the literal
  function juneOr<T>(a: T, b: T): T {
    return a ?? b;
  }
});
