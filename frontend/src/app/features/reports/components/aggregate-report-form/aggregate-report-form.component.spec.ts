import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AggregateReportFormComponent } from './aggregate-report-form.component';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { apiUrl } from '@core/config/api-base-url';
import { CountryService } from '@features/country/services/country.service';
import { DepartmentService } from '@features/department/services/department.service';
import { RoleService } from '@features/role/services/role.service';

describe('AggregateReportFormComponent', () => {
  let component: AggregateReportFormComponent;
  let fixture: ComponentFixture<AggregateReportFormComponent>;

  beforeEach(async () => {
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }));
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }));
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }));
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(of({ content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 }));


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

  it('should initialize with invalid form because startPeriod is required', () => {
    expect(component.form.valid).toBeFalse();
    expect(component.canSubmit()).toBeFalse();
  });

  it('should become valid when startPeriod is set', () => {
    const mockPeriod = {
      id: 'p1',
      name: 'January 2026',
      frequency: 'MONTHLY' as const,
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      closed: false,
    };

    component.form.controls.startPeriod.setValue(mockPeriod);
    fixture.detectChanges();

    expect(component.form.controls.startPeriod.valid).toBeTrue();
    expect(component.canSubmit()).toBeTrue();
  });

  it('should reset form values on onReset', () => {
    spyOn(component.cancel, 'emit');
    component.onReset();

    expect(component.form.controls.startPeriod.value).toBeNull();
    expect(component.form.controls.endPeriod.value).toBeNull();
    expect(component.cancel.emit).toHaveBeenCalled();
  });
});
