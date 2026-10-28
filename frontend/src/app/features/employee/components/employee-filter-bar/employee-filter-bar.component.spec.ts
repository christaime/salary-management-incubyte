import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { EmployeeFilterBarComponent, EmployeeFilterValue } from './employee-filter-bar.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';

const emptyPage = { content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 };

function mockPage(content: any[]) {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 };
}

describe('EmployeeFilterBarComponent', () => {
  let fixture: ComponentFixture<EmployeeFilterBarComponent>;
  let component: EmployeeFilterBarComponent;
  let emitted: EmployeeFilterValue[];

  beforeEach(async () => {
    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(of(mockPage([
      { id: 'c-fr', code: 'FR', name: 'France' },
      { id: 'c-de', code: 'DE', name: 'Germany' },
    ])));

    const departmentSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    departmentSpy.search.and.returnValue(of(mockPage([
      { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    ])));

    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(of(mockPage([
      { id: 'r-dev', code: 'DEV', name: 'Developer' },
    ])));

    await TestBed.configureTestingModule({
      imports: [EmployeeFilterBarComponent],
      providers: [
        provideAnimations(),
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: departmentSpy },
        { provide: RoleService, useValue: roleSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmployeeFilterBarComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.filterChange.subscribe(v => emitted.push(v));
    fixture.detectChanges();
  });

  it('loads jurisdiction, department, and role lookups', () => {
    expect(component.countries().length).toBe(2);
    expect(component.departments().length).toBe(1);
    expect(component.roles().length).toBe(1);
  });

  it('emits filterChange when the keyword changes (debounced 300ms)', fakeAsync(() => {
    component.form.controls.keyword.setValue('alice');
    // No emission yet
    expect(emitted.length).toBe(0);

    tick(299);
    expect(emitted.length).toBe(0);

    tick(1);
    expect(emitted.length).toBe(1);
    expect(emitted[0].keyword).toBe('alice');
  }));

  it('does not emit twice for the same keyword (distinctUntilChanged)', fakeAsync(() => {
    component.form.controls.keyword.setValue('alice');
    tick(300);
    expect(emitted.length).toBe(1);

    component.form.controls.keyword.setValue('alice');
    tick(300);
    expect(emitted.length).toBe(1);
  }));

  it('emits immediately when jurisdiction changes', () => {
    component.form.controls.assignmentCountryId.setValue('c-fr');
    expect(emitted.length).toBe(1);
    expect(emitted[0].assignmentCountryId).toBe('c-fr');
  });

  it('emits immediately when department changes', () => {
    component.form.controls.departmentId.setValue('d-eng');
    expect(emitted.length).toBe(1);
    expect(emitted[0].departmentId).toBe('d-eng');
  });

  it('emits immediately when role changes', () => {
    component.form.controls.roleId.setValue('r-dev');
    expect(emitted.length).toBe(1);
    expect(emitted[0].roleId).toBe('r-dev');
  });

  it('emits immediately when employment type changes', () => {
    component.form.controls.employmentType.setValue('FULL_TIME');
    expect(emitted.length).toBe(1);
    expect(emitted[0].employmentType).toBe('FULL_TIME');
  });

  it('reset clears every filter and emits once per field', fakeAsync(() => {
    component.form.controls.keyword.setValue('alice');
    component.form.controls.assignmentCountryId.setValue('c-fr');
    component.form.controls.departmentId.setValue('d-eng');
    tick(300);
    emitted = [];

    component.onReset();
    tick(300);

    // keyword, country, department reset each fire their valueChanges.
    // Expect at least one emission with all values null/empty.
    const last = emitted[emitted.length - 1];
    expect(last.keyword).toBeNull();
    expect(last.assignmentCountryId).toBeNull();
    expect(last.departmentId).toBeNull();
    expect(last.roleId).toBeNull();
    expect(last.employmentType).toBeNull();
  }));

  it('hasActiveFilters is false on load', () => {
    expect(component.hasActiveFilters()).toBeFalse();
  });

  it('hasActiveFilters is true once any filter is set', () => {
    component.form.controls.employmentType.setValue('FULL_TIME');
    expect(component.hasActiveFilters()).toBeTrue();
  });

  it('renders the reset button disabled when no filters are active', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="filter-reset"]') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });

  it('renders the reset button enabled when a filter is active', () => {
    component.form.controls.employmentType.setValue('FULL_TIME');
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('[data-testid="filter-reset"]') as HTMLButtonElement;
    expect(btn.disabled).toBeFalse();
  });
});
