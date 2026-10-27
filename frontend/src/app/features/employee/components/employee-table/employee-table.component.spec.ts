import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';

import { EmployeeTableComponent } from './employee-table.component';
import { EmployeeWithInEffectAssignmentDto } from '../../models';

function makeEmployee(
  id: string,
  overrides: Partial<EmployeeWithInEffectAssignmentDto> = {},
): EmployeeWithInEffectAssignmentDto {
  return {
    id,
    matricule: `EMP-${id}`,
    name: `Employee ${id}`,
    email: `emp${id}@acme.test`,
    birthDate: '1990-01-01',
    originCountry: { id: 'c-us', code: 'US', name: 'United States' },
    assignmentInEffect: {
      id: "1",
      startDate: '2021-04-01',
      endDate: null,
      country: { id: 'c-us', code: 'US', name: 'United States' },
      department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
      role: { id: 'r-cto', code: 'CTO', name: 'Chief Technology Officer' },
      employmentType: 'FULL_TIME',
      payrollFrequency: 'MONTHLY',
    },
    ...overrides,
  };
}

describe('EmployeeTableComponent', () => {
  let fixture: ComponentFixture<EmployeeTableComponent>;
  let component: EmployeeTableComponent;

  function createWith(
    employees: EmployeeWithInEffectAssignmentDto[],
    loading = false,
  ): void {
    fixture = TestBed.createComponent(EmployeeTableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('employees', employees);
    fixture.componentRef.setInput('loading', loading);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmployeeTableComponent],
      providers: [provideAnimations()],
    });
  });

  // ------------------------------------------------------------------
  // Loading / empty states
  // ------------------------------------------------------------------

  it('renders the loading row when loading is true', () => {
    createWith([], true);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="table-loading"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="table-empty"]')).toBeNull();
    expect(el.querySelectorAll('[data-testid="table-row"]').length).toBe(0);
  });

  it('renders the empty row when there are no employees and not loading', () => {
    createWith([], false);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="table-empty"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="table-loading"]')).toBeNull();
    expect(el.querySelectorAll('[data-testid="table-row"]').length).toBe(0);
  });

  it('renders one row per employee', () => {
    createWith([makeEmployee('1'), makeEmployee('2')]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-testid="table-row"]').length).toBe(2);
  });

  // ------------------------------------------------------------------
  // Row content
  // ------------------------------------------------------------------

  it('renders the employee identity (name, email, matricule)', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="row-name-1"]')?.textContent).toContain('Employee 1');
    expect(el.querySelector('[data-testid="row-email-1"]')?.textContent).toContain('emp1@acme.test');
    expect(el.querySelector('[data-testid="row-matricule-1"]')?.textContent).toContain('EMP-1');
  });

  it('renders the in-effect placement (department + role)', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="row-department-1"]')?.textContent).toContain('Engineering');
    expect(el.querySelector('[data-testid="row-role-1"]')?.textContent).toContain('Chief Technology Officer');
  });

  it('renders the flag emoji for the assignment country', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('🇺🇸');
  });

  it('renders employment type through the pill helper', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;
    const pill = el.querySelector('[data-testid="row-type-1"]');
    expect(pill?.textContent).toContain('Full time');
    expect(pill?.getAttribute('data-employment-type')).toBe('FULL_TIME');
  });

  it('renders the since date from the assignment startDate', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-since-1"]')?.textContent).toContain('2021-04-01');
  });

  it('renders the "In-effect active" status pill when assignmentInEffect is non-null', () => {
    createWith([makeEmployee('1')]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-status-1"]')?.textContent).toContain('In-effect active');
  });

  it('renders the "Draft / Pending" status pill when assignmentInEffect is null', () => {
    createWith([makeEmployee('1', { assignmentInEffect: null })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-status-1"]')?.textContent).toContain('Draft / Pending');
  });

  // ------------------------------------------------------------------
  // Row actions
  // ------------------------------------------------------------------

  it('emits editClick with the employee when Edit is clicked', () => {
    const emp = makeEmployee('1');
    createWith([emp]);

    const spy = jasmine.createSpy('edit');
    component.editClick.subscribe(spy);

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="row-edit-1"]') as HTMLButtonElement).click();

    expect(spy).toHaveBeenCalledOnceWith(emp);
  });

  it('renders Delete (not More) when assignmentInEffect is null', () => {
    createWith([makeEmployee('1', { assignmentInEffect: null })]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="row-delete-1"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="row-more-1"]')).toBeNull();
  });

  it('emits deleteClick with the employee when Delete is clicked', () => {
    const emp = makeEmployee('1', { assignmentInEffect: null });
    createWith([emp]);

    const spy = jasmine.createSpy('delete');
    component.deleteClick.subscribe(spy);

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="row-delete-1"]') as HTMLButtonElement).click();

    expect(spy).toHaveBeenCalledOnceWith(emp);
  });

  it('emits moreClick with the employee when More is clicked', () => {
    const emp = makeEmployee('1');
    createWith([emp]);

    const spy = jasmine.createSpy('more');
    component.moreClick.subscribe(spy);

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="row-more-1"]') as HTMLButtonElement).click();

    expect(spy).toHaveBeenCalledOnceWith(emp);
  });

  // ------------------------------------------------------------------
  // Pure helpers
  // ------------------------------------------------------------------

  it('countryFlag converts ISO codes to emoji', () => {
    createWith([]);
    expect(component.countryFlag('US')).toBe('🇺🇸');
    expect(component.countryFlag('FR')).toBe('🇫🇷');
    expect(component.countryFlag(null)).toBe('');
    expect(component.countryFlag('XYZ')).toBe('');
  });

  it('employmentTypeLabel maps every enum value', () => {
    createWith([]);
    expect(component.employmentTypeLabel('FULL_TIME')).toBe('Full time');
    expect(component.employmentTypeLabel('PART_TIME')).toBe('Part time');
    expect(component.employmentTypeLabel('CONTRACTOR')).toBe('Contractor');
    expect(component.employmentTypeLabel(null)).toBe('—');
  });

  it('formatDate falls back to em-dash for null', () => {
    createWith([]);
    expect(component.formatDate('2026-01-01')).toBe('2026-01-01');
    expect(component.formatDate(null)).toBe('—');
  });
});
