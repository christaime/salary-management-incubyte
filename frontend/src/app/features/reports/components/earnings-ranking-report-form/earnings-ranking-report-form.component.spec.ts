import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { EarningsRankingReportFormComponent } from './earnings-ranking-report-form.component';
import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { PayrollPeriodsService } from '../../../payroll-period/services/payroll-periods.service';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';

describe('EarningsRankingReportFormComponent', () => {
  let component: EarningsRankingReportFormComponent;
  let fixture: ComponentFixture<EarningsRankingReportFormComponent>;

  beforeEach(async () => {
    const emptyPage = { content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 };
    const spyPeriod = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    spyPeriod.search.and.returnValue(of(emptyPage));
    const spyCountry = jasmine.createSpyObj('CountryService', ['search']);
    spyCountry.search.and.returnValue(of(emptyPage));
    const spyDepartment = jasmine.createSpyObj('DepartmentService', ['search']);
    spyDepartment.search.and.returnValue(of(emptyPage));
    const spyRole = jasmine.createSpyObj('RoleService', ['search']);
    spyRole.search.and.returnValue(of(emptyPage));

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

  it('should not allow submit without a period', () => {
    expect(component.canSubmit()).toBeFalse();
  });

  it('should allow submit once a period is selected', () => {
    const period: PayrollPeriodDto = {
      id: 'p1',
      name: 'January 2026',
      frequency: 'MONTHLY',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      closed: false,
    };
    component.form.controls.period.setValue(period);
    fixture.detectChanges();
    expect(component.canSubmit()).toBeTrue();
  });

  it('should hide dimension picker when groupKey is null', () => {
    expect(component.showDimensionPicker()).toBeFalse();
  });

  it('should show the country picker when groupKey = COUNTRY', () => {
    component.form.controls.groupKey.setValue('COUNTRY');
    fixture.detectChanges();
    expect(component.showDimensionPicker()).toBeTrue();
    expect(component.dimensionLabel()).toBe('Country');
    expect(component.dimensionControlName()).toBe('countryId');
  });

  it('should reset dimension values when groupKey changes', () => {
    component.form.controls.groupKey.setValue('COUNTRY');
    component.form.controls.countryId.setValue('c-1');
    component.form.controls.groupKey.setValue('DEPARTMENT');
    expect(component.form.controls.countryId.value).toBeNull();
  });

  it('should emit a correct EarningRankingReportRequest on submit', () => {
    const emitted: any[] = [];
    component.submitForm.subscribe(req => emitted.push(req));

    const period: PayrollPeriodDto = {
      id: 'p1', name: 'January 2026',
      frequency: 'MONTHLY',
      startDate: '2026-01-01', endDate: '2026-01-31', closed: false,
    };
    component.form.patchValue({
      period,
      groupKey: 'DEPARTMENT',
      departmentId: 'd-1',
    });

    component.onSubmit();

    expect(emitted.length).toBe(1);
    expect(emitted[0]).toEqual({
      periodId: 'p1',
      groupKey: 'DEPARTMENT',
      countryId: null,
      departmentId: 'd-1',
      roleId: null,
      employmentType: null,
    });
  });

  it('should emit cancel on reset', () => {
    const spy = spyOn(component.cancel, 'emit');
    component.onReset();
    expect(spy).toHaveBeenCalled();
  });
});
