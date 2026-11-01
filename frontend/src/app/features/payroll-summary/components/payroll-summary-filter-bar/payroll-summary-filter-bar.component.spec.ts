import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

import {
  PayrollFilterValue,
  PayrollSummaryFilterBarComponent,
} from './payroll-summary-filter-bar.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { EmployeeWithInEffectAssignmentDto } from '@features/employee/models';

function period(
  id: string,
  name: string,
  startDate: string,
  endDate: string,
): PayrollPeriodDto {
  return { id, name, startDate, endDate, frequency: 'MONTHLY', closed: false };
}

const JAN_2026 = period('p-jan-2026', 'January 2026', '2026-01-01', '2026-01-31');
const MAR_2026 = period('p-mar-2026', 'March 2026', '2026-03-01', '2026-03-31');
const MAY_2026 = period('p-may-2026', 'May 2026', '2026-05-01', '2026-05-31');
const JUN_2027 = period('p-jun-2027', 'June 2027', '2027-06-01', '2027-06-30');

const emptyPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: true,
};

function mockPage(content: any[]) {
  return { ...emptyPage, content, totalElements: content.length };
}

function fakeEmployee(
  id = 'emp-1',
  matricule = 'EMP-0001',
  name = 'Ada Lovelace',
): EmployeeWithInEffectAssignmentDto {
  return {
    id,
    matricule,
    name,
    email: 'ada@example.com',
    birthDate: null,
    originCountry: null,
    assignmentInEffect: null,
  };
}

describe('PayrollSummaryFilterBarComponent', () => {
  let fixture: ComponentFixture<PayrollSummaryFilterBarComponent>;
  let component: PayrollSummaryFilterBarComponent;
  let emitted: PayrollFilterValue[];

  beforeEach(async () => {
    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(
      of(mockPage([
        { id: 'c-fr', code: 'FR', name: 'France' },
        { id: 'c-de', code: 'DE', name: 'Germany' },
      ])),
    );
    const deptSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    deptSpy.search.and.returnValue(
      of(mockPage([{ id: 'd-eng', code: 'ENG', name: 'Engineering' }])),
    );
    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(
      of(mockPage([{ id: 'r-swe', code: 'SWE', name: 'Software Engineer' }])),
    );
    const periodSpy = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    periodSpy.search.and.returnValue(of(emptyPage));

    await TestBed.configureTestingModule({
      imports: [PayrollSummaryFilterBarComponent, NoopAnimationsModule],
      providers: [
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: deptSpy },
        { provide: RoleService, useValue: roleSpy },
        { provide: PayrollPeriodsService, useValue: periodSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollSummaryFilterBarComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.filterChange.subscribe((v) => emitted.push(v));
    fixture.detectChanges();
  });

  // =========================================================================
  // Initial state
  // =========================================================================

  describe('initial state', () => {
    it('starts with no periods selected and no derived range', () => {
      expect(component.form.controls.startPeriod.value).toBeNull();
      expect(component.form.controls.endPeriod.value).toBeNull();
      expect(component.startPickerSelectedYear()).toBeNull();
      expect(component.endPickerSelectedYear()).toBeNull();
      expect(component.derivedRange()).toBeNull();
      expect(component.canComputeRange()).toBeFalse();
    });

    it('starts with no employee selected', () => {
      expect(component.selectedEmployee()).toBeNull();
    });

    it('loads country / department / role lookups', () => {
      expect(component.countries().length).toBe(2);
      expect(component.departments().length).toBe(1);
      expect(component.roles().length).toBe(1);
    });

    it('renders all filter controls', () => {
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="filter-start-period"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-end-period"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-payroll-frequency"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-status"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-employee-picker"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-employment-type"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-department"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-role"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-country"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-reset"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="filter-apply"]')).not.toBeNull();
    });

    it('disables Apply when nothing has been picked', () => {
      const btn = (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('[data-testid="filter-apply"]')!;
      expect(btn.disabled).toBeTrue();
    });
  });

  // =========================================================================
  // derivedRange
  // =========================================================================

  describe('derivedRange', () => {
    it('rule 1: start year only → full calendar year', () => {
      component.onStartYearChange(2025);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2025-01-01',
        rangeEnd: '2025-12-31',
      });
    });

    it('rule 2: start period only → period boundaries', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2026-01-01',
        rangeEnd: '2026-01-31',
      });
    });

    it('rule 3: start period + end period → period.startDate to endPeriod.endDate', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2026-01-01',
        rangeEnd: '2026-03-31',
      });
    });

    it('rule 4: start period + end year only → period.startDate to Y-12-31', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.onEndYearChange(2027);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2026-01-01',
        rangeEnd: '2027-12-31',
      });
    });

    it('rule 5: start year + end year only → [Y1-01-01, Y2-12-31]', () => {
      component.onStartYearChange(2026);
      component.onEndYearChange(2027);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2026-01-01',
        rangeEnd: '2027-12-31',
      });
    });

    it('rule 6: start year + end period → [Y-01-01, Q.endDate]', () => {
      component.onStartYearChange(2026);
      component.form.controls.endPeriod.setValue(JUN_2027);
      expect(component.derivedRange()).toEqual({
        rangeStart: '2026-01-01',
        rangeEnd: '2027-06-30',
      });
    });
  });

  // =========================================================================
  // canComputeRange
  // =========================================================================

  describe('canComputeRange', () => {
    it('is false when nothing is selected', () => {
      expect(component.canComputeRange()).toBeFalse();
    });

    it('is true with only a start year', () => {
      component.onStartYearChange(2025);
      expect(component.canComputeRange()).toBeTrue();
    });

    it('is true with only a start period', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      expect(component.canComputeRange()).toBeTrue();
    });

    it('is false after reset', () => {
      component.onStartYearChange(2025);
      component.onReset();
      expect(component.canComputeRange()).toBeFalse();
    });
  });

  // =========================================================================
  // Year → state propagation
  // =========================================================================

  describe('start year changes', () => {
    it('updates startPickerSelectedYear', () => {
      component.onStartYearChange(2024);
      expect(component.startPickerSelectedYear()).toBe(2024);
    });

    it('clears an end period whose year is before the new start year', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      component.onStartYearChange(2027);
      expect(component.form.controls.endPeriod.value).toBeNull();
      expect(component.endPeriod()).toBeNull();
    });

    it('keeps an end period whose year is on or after the new start year', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      component.onStartYearChange(2025);
      expect(component.form.controls.endPeriod.value).toEqual(MAR_2026);
    });

    it('clears an end-year that is before the new start year', () => {
      component.onEndYearChange(2025);
      component.onStartYearChange(2026);
      expect(component.endPickerSelectedYear()).toBeNull();
    });
  });

  describe('end year changes', () => {
    it('updates endPickerSelectedYear', () => {
      component.onEndYearChange(2026);
      expect(component.endPickerSelectedYear()).toBe(2026);
    });

    it('clears an end period whose year differs from the new end year', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      component.onEndYearChange(2027);
      expect(component.form.controls.endPeriod.value).toBeNull();
    });

    it('keeps an end period whose year matches the new end year', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      component.onEndYearChange(2026);
      expect(component.form.controls.endPeriod.value).toEqual(MAR_2026);
    });
  });

  // =========================================================================
  // Picker coordination
  // =========================================================================

  describe('picking a start period', () => {
    it('infers startPickerSelectedYear from the period startDate', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      expect(component.startPickerSelectedYear()).toBe(2026);
    });

    it('clears an end period that now precedes the start period endDate', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      component.form.controls.startPeriod.setValue(MAY_2026);
      expect(component.form.controls.endPeriod.value).toBeNull();
    });
  });

  describe('picking an end period', () => {
    it('infers endPickerSelectedYear from the period startDate', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.form.controls.endPeriod.setValue(MAR_2026);
      expect(component.endPickerSelectedYear()).toBe(2026);
    });
  });

  describe('picker coordination', () => {
    it('end picker year range follows the start picker year', () => {
      const fallback = component.endPickerYearRange();
      component.onStartYearChange(2026);
      const withYear = component.endPickerYearRange();
      expect(fallback[0]).toBe(component.currentYear - 5);
      expect(fallback[1]).toBe(component.currentYear);
      expect(withYear).toEqual([2026, component.currentYear]);
    });

    it('end picker selected year follows the start picker year', () => {
      component.onStartYearChange(2025);
      expect(component.endPickerSelectedYearInput()).toBe(2025);
    });

    it('end picker filter starts from the start period endDate', () => {
      expect(component.endPeriodFilter()).toEqual({ frequency: 'MONTHLY' });
      component.form.controls.startPeriod.setValue(JAN_2026);
      expect(component.endPeriodFilter()).toEqual({
        startDate: '2026-01-31',
        frequency: 'MONTHLY',
      });
    });
  });

  // =========================================================================
  // Apply
  // =========================================================================

  describe('Apply', () => {
    it('emits employeeId and employeeName when a picker selection exists', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.onEmployeeSelected(fakeEmployee());
      component.onApply();

      expect(emitted.length).toBe(1);
      expect(emitted[0].employeeId).toBe('emp-1');
      expect(emitted[0].employeeName).toBe('Ada Lovelace');
    });

    it('emits null employeeId/employeeName when no picker selection exists', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.onApply();
      expect(emitted[0].employeeId).toBeNull();
      expect(emitted[0].employeeName).toBeNull();
    });

   it('emits a full filter value with the derived range (rule 2)', () => {
     component.form.patchValue({
       startPeriod: JAN_2026,
       status: 'DRAFT',
       payrollFrequency: 'MONTHLY',
       anyCountryIds: ['c-fr', 'c-de'],
       anyDepartmentIds: ['d-eng'],
       anyRoleIds: ['r-swe'],
       anyEmploymentTypes: ['FULL_TIME'],
     });
     component.onEmployeeSelected(fakeEmployee());
     component.onApply();

     expect(emitted.length).toBe(1);
     expect(emitted[0]).toEqual({
       rangeStart: '2026-01-01',
       rangeEnd: '2026-01-31',
       status: 'DRAFT',
       nonExistent: false,          // NEW
       payrollFrequency: 'MONTHLY',
       employeeId: 'emp-1',
       employeeName: 'Ada Lovelace',
       anyCountryIds: ['c-fr', 'c-de'],
       anyDepartmentIds: ['d-eng'],
       anyRoleIds: ['r-swe'],
       anyEmploymentTypes: ['FULL_TIME'],
     });
   });

    it('emits rule 3 range when both periods are picked', () => {
      component.form.patchValue({ startPeriod: JAN_2026, endPeriod: MAR_2026 });
      component.onApply();
      expect(emitted[0].rangeStart).toBe('2026-01-01');
      expect(emitted[0].rangeEnd).toBe('2026-03-31');
    });

    it('emits rule 4 range when only a start period + end year is set', () => {
      component.form.patchValue({ startPeriod: JAN_2026 });
      component.onEndYearChange(2027);
      component.onApply();
      expect(emitted[0].rangeStart).toBe('2026-01-01');
      expect(emitted[0].rangeEnd).toBe('2027-12-31');
    });

    it('does not emit when there is no start info', () => {
      component.onApply();
      expect(emitted.length).toBe(0);
    });
  });

  // =========================================================================
  // Reset
  // =========================================================================

  describe('Reset', () => {
    it('clears every field, both year signals, both period signals, and the employee', () => {
      component.form.patchValue({
        startPeriod: JAN_2026,
        endPeriod: MAR_2026,
        status: 'VALIDATED',
        payrollFrequency: 'BI_WEEKLY',
        anyCountryIds: ['c-fr'],
        anyDepartmentIds: ['d-eng'],
        anyRoleIds: ['r-swe'],
        anyEmploymentTypes: ['FULL_TIME'],
      });
      component.onStartYearChange(2026);
      component.onEndYearChange(2026);
      component.onEmployeeSelected(fakeEmployee());

      component.onReset();

      const v = component.form.getRawValue();
      expect(v.startPeriod).toBeNull();
      expect(v.endPeriod).toBeNull();
      expect(v.status).toBeNull();
      expect(v.payrollFrequency).toBeNull();
      expect(v.anyCountryIds).toEqual([]);
      expect(v.anyDepartmentIds).toEqual([]);
      expect(v.anyRoleIds).toEqual([]);
      expect(v.anyEmploymentTypes).toEqual([]);
      expect(component.selectedEmployee()).toBeNull();
      expect(component.startPickerSelectedYear()).toBeNull();
      expect(component.endPickerSelectedYear()).toBeNull();
      expect(component.startPeriod()).toBeNull();
      expect(component.endPeriod()).toBeNull();
    });

    it('does not emit a filter value', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      component.onReset();
      expect(emitted.length).toBe(0);
    });
  });

  // =========================================================================
  // Multi-select trigger texts
  // =========================================================================

  describe('multi-select trigger texts', () => {
    it('shows "All jurisdictions" when no country is selected', () => {
      expect(component.countriesTriggerText()).toBe('All jurisdictions');
    });

    it('shows the joined country names when two are selected', () => {
      component.form.patchValue({ anyCountryIds: ['c-fr', 'c-de'] });
      expect(component.countriesTriggerText()).toBe('France, Germany');
    });

    it('shows "All departments" when empty', () => {
      expect(component.departmentsTriggerText()).toBe('All departments');
    });

    it('shows the joined department names', () => {
      component.form.patchValue({ anyDepartmentIds: ['d-eng'] });
      expect(component.departmentsTriggerText()).toBe('Engineering');
    });

    it('shows "All roles" when empty', () => {
      expect(component.rolesTriggerText()).toBe('All roles');
    });

    it('shows the joined role names', () => {
      component.form.patchValue({ anyRoleIds: ['r-swe'] });
      expect(component.rolesTriggerText()).toBe('Software Engineer');
    });

    it('shows "All types" when empty', () => {
      expect(component.employmentTypesTriggerText()).toBe('All types');
    });

    it('shows the joined employment type labels', () => {
      component.form.patchValue({ anyEmploymentTypes: ['FULL_TIME', 'PART_TIME'] });
      expect(component.employmentTypesTriggerText()).toBe(
        'Full time, Part time',
      );
    });

    it('renders the trigger span with the joined names inside the select', () => {
      component.form.patchValue({ anyCountryIds: ['c-fr', 'c-de'] });
      fixture.detectChanges();

      const trigger = fixture.nativeElement.querySelector(
        '[data-testid="filter-country-trigger"]',
      ) as HTMLElement;
      expect(trigger.textContent?.trim()).toBe('France, Germany');
    });
  });

  // =========================================================================
  // Apply button state
  // =========================================================================

  describe('Apply button state', () => {
    it('is enabled once a start year is picked', () => {
      component.onStartYearChange(2026);
      fixture.detectChanges();

      const btn = (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('[data-testid="filter-apply"]')!;
      expect(btn.disabled).toBeFalse();
    });

    it('is enabled once a start period is picked', () => {
      component.form.controls.startPeriod.setValue(JAN_2026);
      fixture.detectChanges();

      const btn = (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('[data-testid="filter-apply"]')!;
      expect(btn.disabled).toBeFalse();
    });

    it('is disabled after Reset', () => {
      component.onStartYearChange(2026);
      fixture.detectChanges();
      component.onReset();
      fixture.detectChanges();

      const btn = (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('[data-testid="filter-apply"]')!;
      expect(btn.disabled).toBeTrue();
    });
  });
});
