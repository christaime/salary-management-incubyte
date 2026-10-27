import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { of, throwError, Observable } from 'rxjs';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { EmployeeListPage } from './employee-list.page';
import { EmployeeService } from '../../services/employee.service';
import { EmployeeFilterBarComponent } from '../../components/employee-filter-bar/employee-filter-bar.component';
import { EmployeeTableComponent } from '../../components/employee-table/employee-table.component';
import { PaginatorComponent } from '@core/components/paginator/paginator.component';
import {
  DataPage
} from '@core/models';

import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import {
  EmployeeWithInEffectAssignmentDto,
} from '../../models';

const emptyPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  empty: true,
  page: 0,
  size: 10,
};

function makeEmployee(id: string): EmployeeWithInEffectAssignmentDto {
  return {
    id,
    matricule: `EMP-${id}`,
    name: `Employee ${id}`,
    email: `emp${id}@acme.test`,
    birthDate: '1990-01-01',
    originCountry: { id: 'c-us', code: 'US', name: 'United States' },
    assignmentInEffect: {
      id:"1",
      startDate: '2021-04-01',
      endDate: null,
      country: { id: 'c-us', code: 'US', name: 'United States' },
      department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
      role: { id: 'r-cto', code: 'CTO', name: 'CTO' },
      employmentType: 'FULL_TIME',
      payrollFrequency: 'MONTHLY',
    },
  };
}

function makePage(content: EmployeeWithInEffectAssignmentDto[]): DataPage<EmployeeWithInEffectAssignmentDto> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: content.length === 0,
    page: 0,
    size: 10,
  };
}

describe('EmployeeListPage', () => {
  let fixture: ComponentFixture<EmployeeListPage>;
  let component: EmployeeListPage;
  let employeeService: jasmine.SpyObj<EmployeeService>;

  beforeEach(async () => {
    employeeService = jasmine.createSpyObj('EmployeeService', ['search']);
    employeeService.search.and.returnValue(of(makePage([])));

    const emptyPage = { content: [], totalElements: 0, totalPages: 1, size: 20, number: 0 };

    const countrySpy = jasmine.createSpyObj('CountryService', ['search']);
    countrySpy.search.and.returnValue(of(emptyPage));
    const deptSpy = jasmine.createSpyObj('DepartmentService', ['search']);
    deptSpy.search.and.returnValue(of(emptyPage));
    const roleSpy = jasmine.createSpyObj('RoleService', ['search']);
    roleSpy.search.and.returnValue(of(emptyPage));

    await TestBed.configureTestingModule({
      imports: [EmployeeListPage, NoopAnimationsModule],
      providers: [
        { provide: EmployeeService, useValue: employeeService },
        { provide: CountryService, useValue: countrySpy },
        { provide: DepartmentService, useValue: deptSpy },
        { provide: RoleService, useValue: roleSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmployeeListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ------------------------------------------------------------------
  // Initial load
  // ------------------------------------------------------------------

  it('loads on construction with default pagination', () => {
    expect(employeeService.search).toHaveBeenCalledOnceWith({
      keyword: undefined,
      anyAssignmentCountryIds: null,
      anyDepartmentIds: null,
      anyRoleIds: null,
      anyEmploymentTypes: null,
      page: 0,
      size: 10,
    } as any);
    expect(component.state()).toBe('success');
  });

  it('renders the title and New Employee button', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="employee-list-title"]')?.textContent)
      .toContain('Employee Directory');
    expect(el.querySelector('[data-testid="employee-list-new"]')).not.toBeNull();
  });

  it('renders the filter bar, table, and paginator', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-employee-filter-bar')).not.toBeNull();
    expect(el.querySelector('app-employee-table')).not.toBeNull();
    expect(el.querySelector('app-paginator')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Filter change → request with criteria
  // ------------------------------------------------------------------

  it('reloads from page 0 when a filter changes', () => {
    employeeService.search.calls.reset();

    component.onFilterChange({
      keyword: 'alice',
      assignmentCountryId: 'c-fr',
      departmentId: 'd-eng',
      roleId: null,
      employmentType: 'FULL_TIME',
    });

    expect(employeeService.search).toHaveBeenCalledOnceWith({
      keyword: 'alice',
      anyAssignmentCountryIds: ['c-fr'],
      anyDepartmentIds: ['d-eng'],
      anyRoleIds: null,
      anyEmploymentTypes: ['FULL_TIME'],
      page: 0,
      size: 10,
    } as any);
  });

  it('forwards filter change to the child filter bar', () => {
    employeeService.search.and.returnValue(of(makePage([])));
    const formDE = fixture.debugElement.query(By.directive(EmployeeFilterBarComponent));
    formDE.componentInstance.filterChange.emit({
      keyword: 'bob',
      assignmentCountryId: null,
      departmentId: null,
      roleId: null,
      employmentType: null,
    });
    fixture.detectChanges();

    expect(employeeService.search).toHaveBeenCalled();
    const lastArg = employeeService.search.calls.mostRecent().args[0];
    expect(lastArg.keyword).toBe('bob');
    expect(lastArg.page).toBe(0);
  });

  // ------------------------------------------------------------------
  // Pagination
  // ------------------------------------------------------------------

  it('reloads when the page changes', () => {
    employeeService.search.calls.reset();

    component.onPageChange({ page: 2, size: 25 });

    expect(employeeService.search).toHaveBeenCalledOnceWith({
      keyword: undefined,
      anyAssignmentCountryIds: null,
      anyDepartmentIds: null,
      anyRoleIds: null,
      anyEmploymentTypes: null,
      page: 2,
      size: 25,
    } as any);
    expect(component.page()).toBe(2);
    expect(component.size()).toBe(25);
  });

  // ------------------------------------------------------------------
  // Success rendering
  // ------------------------------------------------------------------

  it('passes the employees to the table and the total to the paginator', () => {
    employeeService.search.and.returnValue(of(makePage([makeEmployee('1'), makeEmployee('2')])));
    component.load();
    fixture.detectChanges();

    const tableDE = fixture.debugElement.query(By.directive(EmployeeTableComponent));
    expect(tableDE.componentInstance.employees().length).toBe(2);

    const paginatorDE = fixture.debugElement.query(By.directive(PaginatorComponent));
    expect(paginatorDE.componentInstance.totalElements()).toBe(2);
  });

  it('renders the table loading state while a request is in flight', () => {
    // Simulate a request in flight by not settling the observable synchronously
    let settled = false;
    employeeService.search.and.returnValue(new Observable((sub: any) => {
      // no emission until released
      return () => { settled = true; };
    }));

    component.load();
    fixture.detectChanges();
    expect(component.state()).toBe('loading');

    const tableDE = fixture.debugElement.query(By.directive(EmployeeTableComponent));
    expect(tableDE.componentInstance.loading()).toBeTrue();

    expect(settled).toBeFalse();
  });

  // ------------------------------------------------------------------
  // Error rendering
  // ------------------------------------------------------------------

  it('renders the error banner when the request fails', () => {
    employeeService.search.and.returnValue(
      throwError(() => ({ message: 'Network failed' })),
    );
    component.load();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="employee-list-error"]')?.textContent)
      .toContain('Network failed');
    expect(component.state()).toBe('error');
  });

  it('clears the error on a subsequent successful load', () => {
    employeeService.search.and.returnValue(
      throwError(() => ({ message: 'Network failed' })),
    );
    component.load();
    fixture.detectChanges();
    expect(component.state()).toBe('error');

    employeeService.search.and.returnValue(of(makePage([])));
    component.load();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="employee-list-error"]')).toBeNull();
    expect(component.state()).toBe('success');
  });

  // ------------------------------------------------------------------
  // Row actions → page
  // ------------------------------------------------------------------

  it('handles edit row click without throwing', () => {
    expect(() => component.onEdit(makeEmployee('1'))).not.toThrow();
  });

  it('handles delete row click without throwing', () => {
    expect(() => component.onDelete(makeEmployee('1'))).not.toThrow();
  });

  it('handles more row click without throwing', () => {
    expect(() => component.onMore(makeEmployee('1'))).not.toThrow();
  });

  it('handles New Employee click without throwing', () => {
    expect(() => component.onCreateNew()).not.toThrow();
  });

  // ------------------------------------------------------------------
  // Route constants
  // ------------------------------------------------------------------

  it('exposes route constants for future wiring', () => {
    expect(component.newEmployeeRoute).toBe('/employees/new');
    expect(component.employeeEditRoute).toBe('/employees');
  });

  describe('onDelete', () => {
    let dialog: jasmine.SpyObj<MatDialog>;

    beforeEach(() => {
      dialog = jasmine.createSpyObj('MatDialog', ['open']);
      TestBed.overrideProvider(MatDialog, { useValue: dialog });
    });

    it('opens the confirm dialog with the employee name and matricule', () => {
      setup(null);
      const emp = makeEmployee();
      const afterClosed = jasmine.createSpyObj<MatDialogRef<any, boolean>>('MatDialogRef', [], {
        afterClosed: () => of(false),
      });
      dialog.open.and.returnValue(afterClosed);

      component.onDelete(emp);

      expect(dialog.open).toHaveBeenCalled();
      const [_, config] = dialog.open.calls.mostRecent().args;
      expect((config as any).data.message).toContain(emp.name);
      expect((config as any).data.message).toContain(emp.matricule);
    });

    it('does not call the delete endpoint when the user cancels', () => {
      setup(null);
      const emp = makeEmployee();
      dialog.open.and.returnValue({
        afterClosed: () => of(false),
      } as any);

      const service = TestBed.inject(EmployeeService) as jasmine.SpyObj<EmployeeService>;
      component.onDelete(emp);
      expect(service.delete).not.toHaveBeenCalled();
    });

    it('calls the delete endpoint and reloads when the user confirms', () => {
      setup(null);
      const emp = makeEmployee();
      dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

      const service = TestBed.inject(EmployeeService) as jasmine.SpyObj<EmployeeService>;
      service.delete.and.returnValue(of(void 0));
      service.search.and.returnValue(of(makePage([])));

      component.onDelete(emp);

      expect(service.delete).toHaveBeenCalledWith(emp.id);
      expect(service.search).toHaveBeenCalled();
    });

    it('shows an error banner if delete fails', () => {
      setup(null);
      const emp = makeEmployee();
      dialog.open.and.returnValue({ afterClosed: () => of(true) } as any);

      const service = TestBed.inject(EmployeeService) as jasmine.SpyObj<EmployeeService>;
      service.delete.and.returnValue(throwError(() => ({ message: 'Boom' })));

      component.onDelete(emp);
      fixture.detectChanges();

      expect(component.state()).toBe('error');
      expect((fixture.nativeElement as HTMLElement)
        .querySelector('[data-testid="employee-list-error"]')?.textContent)
        .toContain('Boom');
    });
  });
});
