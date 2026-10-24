import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { AggregateReportFilterDrawerComponent, FilterFormGroup } from './aggregate-report-filter-drawer.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';

describe('AggregateReportFilterDrawerComponent', () => {
  let component: AggregateReportFilterDrawerComponent;
  let fixture: ComponentFixture<AggregateReportFilterDrawerComponent>;

  const mockCountryService = { search: () => of({ content: [] }) };
  const mockDepartmentService = { search: () => of({ content: [] }) };
  const mockRoleService = { search: () => of({ content: [] }) };

  let form: FilterFormGroup;

  beforeEach(async () => {
    const fb = new FormBuilder();
    form = fb.group({
      countries: fb.control<string[]>([], { nonNullable: true }),
      departments: fb.control<string[]>([], { nonNullable: true }),
      roles: fb.control<string[]>([], { nonNullable: true }),
      employmentTypes: fb.control<any[]>([], { nonNullable: true }),
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

  it('should toggle open and close state correctly', () => {
    component.open();
    expect(component.isOpen()).toBeTrue();

    component.close();
    expect(component.isOpen()).toBeFalse();
  });

  it('should reset all form controls when clearAll is called', () => {
    form.controls.countries.setValue(['country-1']);
    expect(form.controls.countries.value.length).toBe(1);

    component.clearAll();
    expect(form.controls.countries.value).toEqual([]);
    expect(form.controls.departments.value).toEqual([]);
  });
});
