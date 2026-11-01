import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { EarningsRankingReportFormComponent } from './earnings-ranking-report-form.component';
import { PayrollPeriodsService } from '../../../payroll-period/services/payroll-periods.service';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { GroupKey } from '../../models';
import { RefDto } from '@core/models';

type DimensionControl = 'country' | 'department' | 'role' | 'employmentType';

describe('EarningsRankingReportFormComponent', () => {
  let component: EarningsRankingReportFormComponent;
  let fixture: ComponentFixture<EarningsRankingReportFormComponent>;

  const COUNTRY_FR: RefDto = { id: 'c-fr', code: 'FR', name: 'France' };
  const DEPT_ENG: RefDto = { id: 'd-eng', code: 'ENG', name: 'Engineering' };
  const ROLE_SWE: RefDto = { id: 'r-swe', code: 'SWE', name: 'Software Engineer' };

  const january: PayrollPeriodDto = {
    id: 'p1',
    name: 'January 2026',
    frequency: 'MONTHLY',
    startDate: '2026-01-01',
    endDate: '2026-01-31',
    closed: false,
  };

  beforeEach(async () => {
    const emptyPage = { content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 };
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(of(emptyPage));
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(of({ ...emptyPage, content: [COUNTRY_FR] }));
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(of({ ...emptyPage, content: [DEPT_ENG] }));
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(of({ ...emptyPage, content: [ROLE_SWE] }));

    await TestBed.configureTestingModule({
      imports: [EarningsRankingReportFormComponent],
      providers: [
        provideAnimations(),
        { provide: PayrollPeriodsService, useValue: spyPeriod },
        { provide: CountryService, useValue: spyCountry },
        { provide: DepartmentService, useValue: spyDepartment },
        { provide: RoleService, useValue: spyRole },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EarningsRankingReportFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ------------------------------------------------------------------
  // canSubmit
  // ------------------------------------------------------------------

  it('should not allow submit without a period', () => {
    expect(component.period()).toBeNull();
    expect(component.canSubmit()).toBeFalse();
  });

  it('should allow submit once a period is selected', () => {
    component.form.controls.period.setValue(january);
    fixture.detectChanges();

    expect(component.period()).toEqual(january);
    expect(component.canSubmit()).toBeTrue();
  });

  it('should disable submit while submitting input is true', () => {
    fixture.componentRef.setInput('submitting', true);
    component.form.controls.period.setValue(january);
    fixture.detectChanges();

    expect(component.canSubmit()).toBeFalse();
  });

  // ------------------------------------------------------------------
  // Dimension picker visibility
  // ------------------------------------------------------------------

  it('should hide dimension picker when groupKey is null', () => {
    expect(component.showDimensionPicker()).toBeFalse();
    expect(component.dimensionLabel()).toBe('');
    expect(component.dimensionControlName()).toBeNull();
  });

  it('should show the country picker when groupKey = COUNTRY', () => {
    component.form.controls.groupKey.setValue('COUNTRY');
    fixture.detectChanges();

    expect(component.showDimensionPicker()).toBeTrue();
    expect(component.dimensionLabel()).toBe('Country');
    expect(component.dimensionControlName()).toBe('country');
  });

  it('should map every groupKey to its label + control', () => {
    const cases: Array<[GroupKey, string, DimensionControl]> = [
      ['COUNTRY', 'Country', 'country'],
      ['DEPARTMENT', 'Department', 'department'],
      ['ROLE', 'Role', 'role'],
      ['EMPLOYMENT_TYPE', 'Employment Type', 'employmentType'],
    ];
    for (const [key, label, control] of cases) {
      component.form.controls.groupKey.setValue(key);
      fixture.detectChanges();
      expect(component.dimensionLabel()).toBe(label, `label for ${key}`);
      expect(component.dimensionControlName()).toBe(control, `control for ${key}`);
    }
  });

  // ------------------------------------------------------------------
  // Dimension reset on groupKey change
  // ------------------------------------------------------------------

  it('should reset all dimension values when groupKey changes', () => {
    component.form.controls.groupKey.setValue('COUNTRY');
    component.form.controls.country.setValue(COUNTRY_FR);
    component.form.controls.department.setValue(DEPT_ENG);
    component.form.controls.role.setValue(ROLE_SWE);
    component.form.controls.employmentType.setValue('FULL_TIME');

    component.form.controls.groupKey.setValue('DEPARTMENT');
    fixture.detectChanges();

    expect(component.form.controls.country.value).toBeNull();
    expect(component.form.controls.department.value).toBeNull();
    expect(component.form.controls.role.value).toBeNull();
    expect(component.form.controls.employmentType.value).toBeNull();
  });

  // ------------------------------------------------------------------
  // Submit output
  // ------------------------------------------------------------------

  it('should emit the current EarningsRankingFormValue on submit', () => {
    const emitted: any[] = [];
    component.submitForm.subscribe(v => emitted.push(v));

    component.form.controls.period.setValue(january);
    component.form.controls.groupKey.setValue('DEPARTMENT');
    component.form.controls.department.setValue(DEPT_ENG);

    component.onSubmit();

    expect(emitted.length).toBe(1);
    expect(emitted[0]).toEqual({
      period: january,
      groupKey: 'DEPARTMENT',
      country: null,
      department: DEPT_ENG,
      role: null,
      employmentType: null,
    });
  });

  it('should NOT emit submit when no period is selected', () => {
    const emitted: any[] = [];
    component.submitForm.subscribe(v => emitted.push(v));

    component.form.controls.groupKey.setValue('COUNTRY');
    component.form.controls.country.setValue(COUNTRY_FR);

    component.onSubmit();

    expect(emitted.length).toBe(0);
  });

  // ------------------------------------------------------------------
  // Reset
  // ------------------------------------------------------------------

  it('should reset the form and emit cancel on reset', () => {
    const cancelSpy = spyOn(component.cancel, 'emit');
    const formChangeSpy = spyOn(component.formChange, 'emit');

    component.form.controls.period.setValue(january);
    component.form.controls.groupKey.setValue('COUNTRY');
    component.form.controls.country.setValue(COUNTRY_FR);

    component.onReset();

    expect(component.form.controls.period.value).toBeNull();
    expect(component.form.controls.groupKey.value).toBeNull();
    expect(component.form.controls.country.value).toBeNull();
    expect(component.period()).toBeNull();
    expect(component.groupKey()).toBeNull();
    expect(cancelSpy).toHaveBeenCalled();
    expect(formChangeSpy).toHaveBeenCalledWith(null);
  });

  // ------------------------------------------------------------------
  // formChange emissions
  // ------------------------------------------------------------------

  it('should emit formChange (null) while no period is selected', () => {
    const spy = spyOn(component.formChange, 'emit');

    component.form.controls.groupKey.setValue('COUNTRY');

    expect(spy).toHaveBeenCalledWith(null);
  });

  it('should emit the full form value when a period is set', () => {
    const spy = spyOn(component.formChange, 'emit');

    component.form.controls.period.setValue(january);
    component.form.controls.groupKey.setValue('COUNTRY');
    component.form.controls.country.setValue(COUNTRY_FR);

    const last = spy.calls.mostRecent().args[0];
    expect(last).toEqual({
      period: january,
      groupKey: 'COUNTRY',
      country: COUNTRY_FR,
      department: null,
      role: null,
      employmentType: null,
    });
  });

  // ------------------------------------------------------------------
  // compareRef
  // ------------------------------------------------------------------

  it('compareRef should match by code or name', () => {
    const a: RefDto = { id: 'x', code: 'FR', name: 'France' };
    expect(component.compareRef(a, { id: 'y', code: 'FR', name: 'Other' })).toBeTrue();
    expect(component.compareRef(a, { id: 'y', code: 'DE', name: 'France' })).toBeTrue();
    expect(component.compareRef(a, { id: 'y', code: 'DE', name: 'Germany' })).toBeFalse();
    expect(component.compareRef(null, a)).toBeFalse();
    expect(component.compareRef(a, null)).toBeFalse();
  });
});
