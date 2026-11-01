import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { EmployeePickerComponent } from './employee-picker.component';
import { EmployeeService } from '../../../employee/services/employee.service';
import { EmployeeWithInEffectAssignmentDto } from '@features/employee/models';

function makeEmployee(
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
    assignmentInEffect: {
      id: 'a-1',
      startDate: '2025-10-01',
      endDate: null,
      country: { id: 'c-fr', code: 'FR', name: 'France' },
      department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
      role: { id: 'r-swe', code: 'SWE', name: 'Software Engineer' },
      employmentType: 'FULL_TIME',
      payrollFrequency: 'MONTHLY',
      hasPayroll: true,
    },
  };
}

function makePage(content: EmployeeWithInEffectAssignmentDto[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    page: 0,
    size: 5,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

describe('EmployeePickerComponent', () => {
  let fixture: ComponentFixture<EmployeePickerComponent>;
  let component: EmployeePickerComponent;
  let service: jasmine.SpyObj<EmployeeService>;
  let el: HTMLElement;

  beforeEach(async () => {
    service = jasmine.createSpyObj('EmployeeService', ['search']);
    service.search.and.returnValue(of(makePage([])));

    await TestBed.configureTestingModule({
      imports: [EmployeePickerComponent, NoopAnimationsModule],
      providers: [
        { provide: EmployeeService, useValue: service },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmployeePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  });

  // ------------------------------------------------------------------
  // Initial render
  // ------------------------------------------------------------------

  it('renders the search input when nothing is selected', () => {
    expect(el.querySelector('[data-testid="employee-search-input"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="employee-selected-card"]')).toBeNull();
    expect(el.querySelector('[data-testid="employee-search-results"]')).toBeNull();
  });

  it('starts with no results and no error', () => {
    expect(component.results()).toEqual([]);
    expect(component.searchError()).toBeNull();
    expect(component.searching()).toBeFalse();
    expect(component.selected()).toBeNull();
  });

  // ------------------------------------------------------------------
  // Debounced search
  // ------------------------------------------------------------------

  it('does NOT search below 3 characters', fakeAsync(() => {
    component.keyword.setValue('ab');
    tick(500);
    expect(service.search).not.toHaveBeenCalled();
  }));

  it('searches automatically after 300ms once 3+ chars are entered', fakeAsync(() => {
    service.search.and.returnValue(of(makePage([makeEmployee()])));
    component.keyword.setValue('ada');
    tick(300);

    expect(service.search).toHaveBeenCalledOnceWith({
      keyword: 'ada',
      page: 0,
      size: 5,
    });
    expect(component.results().length).toBe(1);
    expect(component.searching()).toBeFalse();
  }));

  it('does not re-fire for the same keyword', fakeAsync(() => {
    component.keyword.setValue('ada');
    tick(300);
    component.keyword.setValue('ada');
    tick(300);
    // distinctUntilChanged suppresses the duplicate emission
    expect(service.search).toHaveBeenCalledTimes(1);
  }));

  it('only fires for the final keyword when typing fast', fakeAsync(() => {
    component.keyword.setValue('a');
    component.keyword.setValue('ad');
    component.keyword.setValue('ada');
    tick(300);
    expect(service.search).toHaveBeenCalledTimes(1);
    expect(service.search.calls.mostRecent().args[0].keyword).toBe('ada');
  }));

  it('trims the keyword before searching', fakeAsync(() => {
    component.keyword.setValue('   ada   ');
    tick(300);
    expect(service.search.calls.mostRecent().args[0].keyword).toBe('ada');
  }));

  // ------------------------------------------------------------------
  // Rendering results
  // ------------------------------------------------------------------

  it('renders results in the dropdown after a search', fakeAsync(() => {
    service.search.and.returnValue(of(makePage([makeEmployee('e-1', 'EMP-1', 'Ada')])));
    component.keyword.setValue('ada');
    tick(300);
    fixture.detectChanges();

    const list = el.querySelector('[data-testid="employee-search-results"]');
    expect(list).not.toBeNull();
    expect(el.querySelector('[data-testid="employee-result-e-1"]')?.textContent)
      .toContain('Ada');
    expect(el.querySelector('[data-testid="employee-result-e-1"]')?.textContent)
      .toContain('EMP-1');
  }));

  it('shows a "No in-effect assignment" hint for employees without one', fakeAsync(() => {
    const noAssignment = { ...makeEmployee(), assignmentInEffect: null };
    service.search.and.returnValue(of(makePage([noAssignment])));
    component.keyword.setValue('ada');
    tick(300);
    fixture.detectChanges();

    const result = el.querySelector('[data-testid="employee-result-emp-1"]');
    expect(result?.textContent).toContain('No in-effect assignment');
  }));

  it('shows the search spinner while searching', () => {
    component['searching'].set(true);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="employee-search-spinner"]')).not.toBeNull();
    component['searching'].set(false);
  });

  // ------------------------------------------------------------------
  // Errors
  // ------------------------------------------------------------------

  it('renders an error and clears results when the search fails', fakeAsync(() => {
    service.search.and.returnValue(
      throwError(() => ({ message: 'Backend down' })),
    );
    component.keyword.setValue('ada');
    tick(300);
    fixture.detectChanges();

    const err = el.querySelector('[data-testid="employee-search-error"]');
    expect(err?.textContent).toContain('Backend down');
    expect(component.results()).toEqual([]);
    expect(component.searching()).toBeFalse();
  }));

  it('falls back to a generic message when the error has none', fakeAsync(() => {
    service.search.and.returnValue(throwError(() => ({})));
    component.keyword.setValue('ada');
    tick(300);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="employee-search-error"]')?.textContent)
      .toContain('Search failed');
  }));

  // ------------------------------------------------------------------
  // onSearch (Enter shortcut)
  // ------------------------------------------------------------------

  it('onSearch does nothing when fewer than 3 chars', () => {
    component.keyword.setValue('ab');
    service.search.calls.reset();
    component.onSearch();
    expect(service.search).not.toHaveBeenCalled();
  });

  it('onSearch fires a fetch immediately for a valid keyword', fakeAsync(() => {
    service.search.and.returnValue(of(makePage([makeEmployee()])));
    component.keyword.setValue('ada');
    service.search.calls.reset();

    component.onSearch();
    // No tick needed: triggerSearchNow subscribes synchronously
    expect(service.search).toHaveBeenCalledOnceWith({
      keyword: 'ada',
      page: 0,
      size: 5,
    });
    expect(component.results().length).toBe(1);
    tick(300); // flush the debounced pipeline
  }));

  it('onSearch reports errors', () => {
    service.search.and.returnValue(throwError(() => ({ message: 'Nope' })));
    component.keyword.setValue('ada');
    component.onSearch();
    fixture.detectChanges();
    expect(component.searchError()).toBe('Nope');
    expect(component.results()).toEqual([]);
    expect(component.searching()).toBeFalse();
  });

  // ------------------------------------------------------------------
  // Selection
  // ------------------------------------------------------------------

  it('selectEmployee stores the selection, clears results/keyword, and emits', () => {
    const spy = jasmine.createSpy('selectedChange');
    component.selectedChange.subscribe(spy);
    const emp = makeEmployee();

    component.selectEmployee(emp);
    fixture.detectChanges();

    expect(component.selected()).toBe(emp);
    expect(component.results()).toEqual([]);
    expect(component.keyword.value).toBe('');
    expect(spy).toHaveBeenCalledOnceWith(emp);
  });

  it('renders the selected card (and hides the search input) after selection', () => {
    component.selectEmployee(makeEmployee());
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="employee-search-input"]')).toBeNull();
    expect(el.querySelector('[data-testid="employee-selected-card"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="employee-selected-badge"]')?.textContent?.trim())
      .toBe('EMP-0001');
    expect(el.querySelector('[data-testid="employee-selected-name"]')?.textContent?.trim())
      .toBe('Ada Lovelace');
  });

  it('does not render the results dropdown when an employee is selected', fakeAsync(() => {
    service.search.and.returnValue(of(makePage([makeEmployee()])));
    component.selectEmployee(makeEmployee());
    fixture.detectChanges();

    // Even if results were populated, the dropdown is guarded by !selected()
    component.results.set([makeEmployee()]);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="employee-search-results"]')).toBeNull();
    tick(300);
  }));

  // ------------------------------------------------------------------
  // Clear
  // ------------------------------------------------------------------

  it('clearSelection resets state, emits null, and restores the search input', () => {
    const spy = jasmine.createSpy('selectedChange');
    component.selectedChange.subscribe(spy);
    component.selectEmployee(makeEmployee());
    spy.calls.reset();

    component.clearSelection();
    fixture.detectChanges();

    expect(component.selected()).toBeNull();
    expect(spy).toHaveBeenCalledOnceWith(null);
    expect(el.querySelector('[data-testid="employee-search-input"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="employee-selected-card"]')).toBeNull();
  });

  it('clicking the clear button emits null and restores the input', () => {
    const spy = jasmine.createSpy('selectedChange');
    component.selectedChange.subscribe(spy);
    component.selectEmployee(makeEmployee());
    spy.calls.reset();
    fixture.detectChanges();

    (el.querySelector<HTMLButtonElement>('[data-testid="employee-selected-clear"]')!)
      .click();
    fixture.detectChanges();

    expect(component.selected()).toBeNull();
    expect(spy).toHaveBeenCalledOnceWith(null);
    expect(el.querySelector('[data-testid="employee-search-input"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // canSearch
  // ------------------------------------------------------------------

  it('canSearch reflects the 3-char minimum', () => {
    component.keyword.setValue('ab');
    expect(component.canSearch()).toBeFalse();
    component.keyword.setValue('abc');
    expect(component.canSearch()).toBeTrue();
    component.keyword.setValue('   abc   ');
    expect(component.canSearch()).toBeTrue();
  });
});
