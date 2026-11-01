import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { of } from 'rxjs';
import { AggregateReportFilterDrawerComponent } from './aggregate-report-filter-drawer.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';

interface Ref {
  id: string;
  code: string;
  name: string;
}

type FilterFormGroup = FormGroup<{
  countries: FormControl<Ref[]>;
  departments: FormControl<Ref[]>;
  roles: FormControl<Ref[]>;
  employmentTypes: FormControl<string[]>;
}>;

describe('AggregateReportFilterDrawerComponent', () => {
  let component: AggregateReportFilterDrawerComponent;
  let fixture: ComponentFixture<AggregateReportFilterDrawerComponent>;

  const COUNTRY_FR: Ref = { id: 'c-fr', code: 'FR', name: 'France' };
  const COUNTRY_DE: Ref = { id: 'c-de', code: 'DE', name: 'Germany' };
  const DEPT_ENG: Ref = { id: 'd-eng', code: 'ENG', name: 'Engineering' };
  const ROLE_SWE: Ref = { id: 'r-swe', code: 'SWE', name: 'Software Engineer' };

  const mockCountryService = { search: () => of({ content: [COUNTRY_FR, COUNTRY_DE] }) };
  const mockDepartmentService = { search: () => of({ content: [DEPT_ENG] }) };
  const mockRoleService = { search: () => of({ content: [ROLE_SWE] }) };

  let form: FilterFormGroup;

  beforeEach(async () => {
    const fb = new FormBuilder();
    form = fb.group({
      countries: fb.control<Ref[]>([], { nonNullable: true }),
      departments: fb.control<Ref[]>([], { nonNullable: true }),
      roles: fb.control<Ref[]>([], { nonNullable: true }),
      employmentTypes: fb.control<string[]>([], { nonNullable: true }),
    }) as FilterFormGroup;

    await TestBed.configureTestingModule({
      imports: [AggregateReportFilterDrawerComponent],
      providers: [
        provideAnimations(),
        { provide: CountryService, useValue: mockCountryService },
        { provide: DepartmentService, useValue: mockDepartmentService },
        { provide: RoleService, useValue: mockRoleService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateReportFilterDrawerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('form', form);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load lookup data on init', () => {
    expect(component.countries()).toEqual([COUNTRY_FR, COUNTRY_DE]);
    expect(component.departments()).toEqual([DEPT_ENG]);
    expect(component.roles()).toEqual([ROLE_SWE]);
  });

  it('should toggle open and close state correctly', () => {
    component.open();
    expect(component.isOpen()).toBeTrue();

    component.close();
    expect(component.isOpen()).toBeFalse();
  });

  it('open() should copy parent form values into the local draft', () => {
    form.controls.countries.setValue([COUNTRY_FR]);
    form.controls.employmentTypes.setValue(['FULL_TIME']);

    component.open();

    const draft = component.localForm.getRawValue();
    expect(draft.countries).toEqual([COUNTRY_FR]);
    expect(draft.employmentTypes).toEqual(['FULL_TIME']);
  });

  it('clearAll() should reset only the local draft, not the parent form', () => {
    form.controls.countries.setValue([COUNTRY_FR]);
    form.controls.departments.setValue([DEPT_ENG]);

    component.open();
    component.clearAll();

    // Local draft is cleared...
    expect(component.localForm.getRawValue().countries).toEqual([]);
    expect(component.localForm.getRawValue().departments).toEqual([]);

    // ...but the parent form is untouched until Apply.
    expect(form.controls.countries.value).toEqual([COUNTRY_FR]);
    expect(form.controls.departments.value).toEqual([DEPT_ENG]);
  });

  it('apply() should commit the local draft to the parent form and close the drawer', () => {
    const appliedSpy = spyOn(component.applied, 'emit');

    component.open();
    component.localForm.patchValue({
      countries: [COUNTRY_FR],
      departments: [DEPT_ENG],
      roles: [ROLE_SWE],
      employmentTypes: ['FULL_TIME'],
    });

    component.apply();

    expect(form.controls.countries.value).toEqual([COUNTRY_FR]);
    expect(form.controls.departments.value).toEqual([DEPT_ENG]);
    expect(form.controls.roles.value).toEqual([ROLE_SWE]);
    expect(form.controls.employmentTypes.value).toEqual(['FULL_TIME']);
    expect(appliedSpy).toHaveBeenCalled();
    expect(component.isOpen()).toBeFalse();
  });

  it('apply() after clearAll() should leave the parent form empty', () => {
    form.controls.countries.setValue([COUNTRY_FR]);

    component.open();
    component.clearAll();
    component.apply();

    expect(form.controls.countries.value).toEqual([]);
    expect(form.controls.departments.value).toEqual([]);
    expect(form.controls.roles.value).toEqual([]);
    expect(form.controls.employmentTypes.value).toEqual([]);
  });

  it('should close on Escape when open', () => {
    component.open();
    component.onEscape();
    expect(component.isOpen()).toBeFalse();
  });

  it('compareRef() should match by id or code', () => {
    const a: Ref = { id: 'x', code: 'XX', name: 'A' };
    expect(component.compareRef(a, { id: 'x', code: 'other', name: 'B' })).toBeTrue();
    expect(component.compareRef(a, { id: 'other', code: 'XX', name: 'B' })).toBeTrue();
    expect(component.compareRef(a, { id: 'other', code: 'other', name: 'B' })).toBeFalse();
    expect(component.compareRef(null, a)).toBeFalse();
    expect(component.compareRef(a, null)).toBeFalse();
  });

  it('should show "All N …" display text when a filter is empty', () => {
    component.open();

    expect(component.countryDisplayText()).toBe('All 2 countries');
    expect(component.departmentDisplayText()).toBe('All 1 departments');
    expect(component.roleDisplayText()).toBe('All 1 roles');
  });

  it('should show the selected names in display text', () => {
    component.open();
    component.localForm.patchValue({ countries: [COUNTRY_FR] });

    expect(component.countryDisplayText()).toBe('France');
    // No tooltip for a single selection.
    expect(component.countryTooltip()).toBe('');
  });

  it('should populate the tooltip when more than 2 items are selected', () => {
    component.open();
    const extra: Ref = { id: 'c-es', code: 'ES', name: 'Spain' };
    component.countries.set([COUNTRY_FR, COUNTRY_DE, extra]);

    component.localForm.patchValue({ countries: [COUNTRY_FR, COUNTRY_DE, extra] });

    expect(component.countryTooltip()).toBe('France, Germany, Spain');
  });
});
