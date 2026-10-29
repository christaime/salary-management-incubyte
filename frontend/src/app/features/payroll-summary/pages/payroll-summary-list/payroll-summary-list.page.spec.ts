import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { PayrollSummaryListPage } from './payroll-summary-list.page';
import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryFilterBarComponent } from '../../components/payroll-summary-filter-bar/payroll-summary-filter-bar.component';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { PayrollPeriodsService } from '@features/payroll-period/services/payroll-periods.service';
import { DataPage } from '@core/models';
import {
  PayrollSummaryDetailDto,
  PayrollSummaryDto,
} from '../../models/payroll-summary.model';
import { PayrollFilterValue } from '../../components/payroll-summary-filter-bar/payroll-summary-filter-bar.component';

const emptyPage: DataPage<PayrollSummaryDto> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
  first: true,
  last: true,
  empty: true,
};

function makeRow(
  id = 's-1',
  status: 'DRAFT' | 'VALIDATED' = 'DRAFT',
): PayrollSummaryDto {
  return {
    id,
    status,
    employee: { id: 'e-1', matricule: 'EMP-0001', name: 'Ada Lovelace' },
    period: {
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    },
    country: { id: 'c-fr', code: 'FR', name: 'France' },
    department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    role: { id: 'r-swe', code: 'SWE', name: 'Software Engineer' },
    employmentType: 'FULL_TIME',
    amounts: {
      base: 1000,
      variable: 100,
      allowances: 50,
      employerTaxes: 200,
      total: 1350,
      currency: 'EUR',
    },
    // PayrollSummaryDto requires an assignment (with frequency)
    assignment: {
      id: 'a-1',
      startDate: '2025-10-01',
      endDate: null,
      frequency: 'MONTHLY',
    },
  };
}

function makeDetail(
           id = 's-1',
           status: 'DRAFT' | 'VALIDATED' = 'DRAFT',
         ): PayrollSummaryDetailDto {
  return {
    ...makeRow(id),
    assignment: { id: 'a-1', startDate: '2025-10-01', endDate: null, hasPayroll: true },
  };
}

function makePage(
  content: PayrollSummaryDto[],
): DataPage<PayrollSummaryDto> {
  const size = 20;
  const totalElements = content.length;
  return {
    content,
    totalElements,
    page: 0,
    size,
    totalPages: Math.max(1, Math.ceil(totalElements / size)),
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

describe('PayrollSummaryListPage', () => {
  let fixture: ComponentFixture<PayrollSummaryListPage>;
  let component: PayrollSummaryListPage;
  let service: jasmine.SpyObj<PayrollSummaryService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let router: Router;

  function filterValue(): PayrollFilterValue {
    return {
      rangeStart: '2026-01-01',
      rangeEnd: '2026-03-31',
      status: null,
      nonExistent: false, // NEW: required by the interface
      payrollFrequency: null,
      employeeId: null,   // NEW
      employeeName: null, // NEW
      anyCountryIds: [],
      anyDepartmentIds: [],
      anyRoleIds: [],
      anyEmploymentTypes: [],
    };
  }

  beforeEach(async () => {
    service = jasmine.createSpyObj('PayrollSummaryService', [
      'search',
      'validate',
      'delete',
      'clearSelectedNonExistent',
      'setSelectedNonExistent',
    ]);
    service.search.and.returnValue(of(makePage([])));
    service.validate.and.returnValue(of(makeDetail()));
    service.delete.and.returnValue(of(void 0));

    dialog = jasmine.createSpyObj('MatDialog', ['open']);

    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(of(emptyPage));
    const deptSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    deptSpy.search.and.returnValue(of(emptyPage));
    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(of(emptyPage));
    const periodSpy = jasmine.createSpyObj('PayrollPeriodsService', ['search']);
    periodSpy.search.and.returnValue(of(emptyPage));

    await TestBed.configureTestingModule({
      imports: [PayrollSummaryListPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: PayrollSummaryService, useValue: service },
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: deptSpy },
        { provide: RoleService, useValue: roleSpy },
        { provide: PayrollPeriodsService, useValue: periodSpy },
        { provide: MatDialog, useValue: dialog },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollSummaryListPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
  });

  // ... (existing tests unchanged, they now type-check) ...

  it('starts idle and does not call search on construction', () => {
    expect(component.state()).toBe('idle');
    expect(service.search).not.toHaveBeenCalled();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="payroll-summary-list-idle"]')).not.toBeNull();
    expect(el.querySelector('app-payroll-summary-table')).toBeNull();
  });

  it('calls search on filter change and renders the table', () => {
    service.search.and.returnValue(
      of(makePage([makeRow('s-1'), makeRow('s-2', 'VALIDATED')])),
    );

    component.onFilterChange(filterValue());
    fixture.detectChanges();

    expect(service.search).toHaveBeenCalledTimes(1);
    const args = service.search.calls.mostRecent().args[0];
    expect(args.rangeStart).toBe('2026-01-01');
    expect(args.rangeEnd).toBe('2026-03-31');
    expect(args.page).toBe(0);
    expect(args.size).toBe(20);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-payroll-summary-table')).not.toBeNull();
    expect(el.querySelector('[data-testid="payroll-row-s-1"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="payroll-row-s-2"]')).not.toBeNull();
  });

  it('renders the error banner when search fails', () => {
    service.search.and.returnValue(
      throwError(() => ({ message: 'Network failed' })),
    );

    component.onFilterChange(filterValue());
    fixture.detectChanges();

    expect(component.state()).toBe('error');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        '[data-testid="payroll-summary-list-error"]',
      )?.textContent,
    ).toContain('Network failed');
  });

  it('reloads when the page changes', () => {
    component.onFilterChange(filterValue());
    service.search.calls.reset();

    component.onPageChange({ page: 3, size: 50 });

    const args = service.search.calls.mostRecent().args[0];
    expect(args.page).toBe(3);
    expect(args.size).toBe(50);
  });

  it('navigates to the detail page on row click', () => {
    component.onRowClick(makeRow('s-1'));
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries', 's-1']);
  });

  it('navigates to the editor on edit', () => {
    component.onEdit(makeRow('s-1'));
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries', 's-1', 'edit']);
  });

  it('navigates to the create page on new', () => {
    component.onNew();
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries/new']);
  });

  it('opens a confirm dialog on Validate and calls validate on confirm', () => {
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);
    component.onValidate(makeRow('s-1'));
    expect(dialog.open).toHaveBeenCalled();
    expect(service.validate).toHaveBeenCalledWith('s-1');
  });

  it('does not call validate when the confirm dialog is dismissed', () => {
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);
    component.onValidate(makeRow('s-1'));
    expect(service.validate).not.toHaveBeenCalled();
  });

  it('opens a confirm dialog on Delete and calls delete on confirm', () => {
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);
    component.onDelete(makeRow('s-1'));
    expect(service.delete).toHaveBeenCalledWith('s-1');
  });

  it('does not call delete when the confirm dialog is dismissed', () => {
    dialog.open.and.returnValue({ afterClosed: () => of(false) } as any);
    component.onDelete(makeRow('s-1'));
    expect(service.delete).not.toHaveBeenCalled();
  });

  // ----- UPDATED: bulk-validate tests use the new API -----

  it('does not open the bulk dialog when no period is selected', () => {
    component.onBulkValidate();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('opens the bulk dialog when a period is selected', () => {
    // NEW: use selectedPeriod signal directly (no availablePeriods / onPeriodSelect)
    component.selectedPeriod.set({
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    });
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

    component.onBulkValidate();

    expect(dialog.open).toHaveBeenCalled();
    const [, config] = dialog.open.calls.mostRecent().args;
    expect((config as any).data.periodId).toBe('p-1');
  });

  // ----- NEW tests to backfill coverage -----

  it('onPeriodSelected stores the period and enables bulk validate', () => {
    component.selectedPeriod.set(null);
    expect(component.canBulkValidate()).toBeFalse();

    component.onPeriodSelected({
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    });

    expect(component.selectedPeriod()?.id).toBe('p-1');
    expect(component.canBulkValidate()).toBeTrue();
  });

  it('onPeriodSelected(null) disables bulk validate', () => {
    component.onPeriodSelected({
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    });
    component.onPeriodSelected(null);
    expect(component.selectedPeriod()).toBeNull();
    expect(component.canBulkValidate()).toBeFalse();
  });

  it('onFilterChange clears the non-existent selection and reloads', () => {
    component.onFilterChange({ ...filterValue(), nonExistent: true });

    expect(service.clearSelectedNonExistent).toHaveBeenCalled();
    expect(component.nonExistentPayroll()).toBeTrue();
    expect(service.search).toHaveBeenCalled();
  });

  it('onRowClick on a non-existent row routes to the create page and stashes the row', () => {
    component.onFilterChange({ ...filterValue(), nonExistent: true });
    service.search.calls.reset();

    component.onRowClick(makeRow('s-1'));

    expect(service.setSelectedNonExistent).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries/new']);
  });

  it('onEdit on a non-existent row routes to the create page and stashes the row', () => {
    component.onFilterChange({ ...filterValue(), nonExistent: true });
    service.search.calls.reset();

    component.onEdit(makeRow('s-1'));

    expect(service.setSelectedNonExistent).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/payroll-summaries/new']);
  });

  it('onFilterReset returns to idle and clears rows/totals/selection', () => {
    // get into success state first
    component.onFilterChange(filterValue());
    expect(component.state()).toBe('success');

    component.onFilterReset();

    expect(component.state()).toBe('idle');
    expect(component.rows()).toEqual([]);
    expect(component.totalElements()).toBe(0);
    expect(component.selectedPeriod()).toBeNull();
    expect(component.nonExistentPayroll()).toBeFalse();
  });

  it('builds filter chips from the active filter value', () => {
    component.onFilterChange({
      ...filterValue(),
      status: 'DRAFT',
      payrollFrequency: 'MONTHLY',
      employeeId: 'e-1',
      employeeName: 'Ada Lovelace',
      anyCountryIds: ['c-1'],
      anyDepartmentIds: ['d-1', 'd-2'],
      anyRoleIds: ['r-1'],
      anyEmploymentTypes: ['FULL_TIME'],
    });

    const chips = component.filterChips();
    expect(chips).toContain('Status: DRAFT');
    expect(chips).toContain('Frequency: MONTHLY');
    expect(chips).toContain('Employee: Ada Lovelace');
    expect(chips).toContain('Countries: 1');
    expect(chips).toContain('Departments: 2');
    expect(chips).toContain('Roles: 1');
    expect(chips).toContain('Types: 1');
  });

  it('filterChips falls back to "Employee: selected" when no name is provided', () => {
    component.onFilterChange({
      ...filterValue(),
      employeeId: 'e-1',
      employeeName: null,
    });
    expect(component.filterChips()).toContain('Employee: selected');
  });

  it('bulkValidateLabel shows the default when no period is selected', () => {
    component.selectedPeriod.set(null);
    expect(component.bulkValidateLabel()).toBe('Validate all drafts');
  });

  it('bulkValidateLabel includes the period name and criteria suffix', () => {
    component.selectedPeriod.set({
      id: 'p-1',
      name: 'January 2026',
      startDate: '2026-01-01',
      endDate: '2026-01-31',
      frequency: 'MONTHLY',
      closed: false,
    });
    component.onFilterChange({ ...filterValue(), status: 'DRAFT' });

    expect(component.bulkValidateLabel()).toContain('January 2026');
    expect(component.bulkValidateLabel()).toContain('corresponding to the criteria');
  });

  it('passes filters to the child filter bar via the output', () => {
    const formDE = fixture.debugElement.query(
      By.directive(PayrollSummaryFilterBarComponent),
    );

    formDE.componentInstance.form.patchValue({
      startPeriod: {
        id: 'p-jan',
        name: 'January 2026',
        startDate: '2026-01-01',
        endDate: '2026-01-31',
        frequency: 'MONTHLY',
        closed: false,
      },
    });

    const spy = jasmine.createSpy('filterChange');
    formDE.componentInstance.filterChange.subscribe(spy);

    formDE.componentInstance.onApply();

    expect(spy).toHaveBeenCalled();
    const payload = spy.calls.mostRecent().args[0] as PayrollFilterValue;
    // The new required fields should be present
    expect(payload).toEqual(
      jasmine.objectContaining({
        nonExistent: false,
        employeeId: null,
        employeeName: null,
      }),
    );
  });
});
