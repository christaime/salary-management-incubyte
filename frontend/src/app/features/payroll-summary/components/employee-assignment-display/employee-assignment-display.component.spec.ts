import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { EmployeeAssignmentDisplayComponent } from './employee-assignment-display.component';
import { EmployeeWithInEffectAssignmentDto } from '@features/employee/models';

function makeEmployee(
  overrides: Partial<EmployeeWithInEffectAssignmentDto> = {},
): EmployeeWithInEffectAssignmentDto {
  return {
    id: 'emp-1',
    matricule: 'EMP-0001',
    name: 'Ada Lovelace',
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
    ...overrides,
  };
}

function makeNoAssignment(): EmployeeWithInEffectAssignmentDto {
  return makeEmployee({ assignmentInEffect: null });
}

describe('EmployeeAssignmentDisplayComponent', () => {
  let fixture: ComponentFixture<EmployeeAssignmentDisplayComponent>;
  let component: EmployeeAssignmentDisplayComponent;
  let el: HTMLElement;

  function createWith(
    employee: EmployeeWithInEffectAssignmentDto,
    onlyAssignment = false,
  ): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [EmployeeAssignmentDisplayComponent, NoopAnimationsModule],
    });
    fixture = TestBed.createComponent(EmployeeAssignmentDisplayComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('employee', employee);
    fixture.componentRef.setInput('onlyAssignment', onlyAssignment);
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  }

  // ------------------------------------------------------------------
  // Selected employee bar
  // ------------------------------------------------------------------

  it('renders the employee bar with matricule and name by default', () => {
    createWith(makeEmployee());
    const card = el.querySelector('[data-testid="employee-display-selected-card"]');
    expect(card).not.toBeNull();
    expect(el.querySelector('[data-testid="employee-display-badge"]')?.textContent?.trim())
      .toBe('EMP-0001');
    expect(el.querySelector('[data-testid="employee-display-name"]')?.textContent?.trim())
      .toBe('Ada Lovelace');
  });

  it('hides the employee bar when onlyAssignment is true', () => {
    createWith(makeEmployee(), true);
    expect(el.querySelector('[data-testid="employee-display-selected-card"]')).toBeNull();
    // Pills still render
    expect(el.querySelector('[data-testid="assignment-pill-summary"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // Assignment pill summary
  // ------------------------------------------------------------------

  it('renders the country pill with flag, code and name', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-country-FR"]');
    expect(pill).not.toBeNull();
    expect(pill?.textContent).toContain('FR');
    expect(pill?.textContent).toContain('France');
    expect(pill?.textContent).toContain('🇫🇷');
    expect(pill?.getAttribute('title')).toBe('France');
  });

  it('renders the department pill with code and name', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-department-ENG"]');
    expect(pill).not.toBeNull();
    expect(pill?.textContent).toContain('ENG');
    expect(pill?.textContent).toContain('Engineering');
    expect(pill?.getAttribute('title')).toBe('Engineering');
  });

  it('renders the role pill with code and name', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-role-SWE"]');
    expect(pill).not.toBeNull();
    expect(pill?.textContent).toContain('SWE');
    expect(pill?.textContent).toContain('Software Engineer');
  });

  it('renders the employment type pill with the canonical label', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-employment-type"]');
    expect(pill?.textContent).toContain('Full time');
  });

  it('renders the frequency pill with the canonical label', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-frequency"]');
    expect(pill?.textContent).toContain('Monthly');
  });

  it('renders the start date pill', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-start"]');
    expect(pill?.textContent).toContain('2025-10-01');
  });

  it('renders "Open-ended" when endDate is null', () => {
    createWith(makeEmployee());
    const pill = el.querySelector('[data-testid="assignment-pill-end"]');
    expect(pill?.textContent).toContain('Open-ended');
  });

  it('renders the end date when set', () => {
    createWith(
      makeEmployee({
        assignmentInEffect: {
          ...makeEmployee().assignmentInEffect!,
          endDate: '2027-12-31',
        },
      }),
    );
    const pill = el.querySelector('[data-testid="assignment-pill-end"]');
    expect(pill?.textContent).toContain('2027-12-31');
    expect(pill?.textContent).not.toContain('Open-ended');
  });

  // ------------------------------------------------------------------
  // No-assignment state
  // ------------------------------------------------------------------

  it('renders the no-assignment error and hides the pill summary', () => {
    createWith(makeNoAssignment());
    const error = el.querySelector('[data-testid="employee-selected-none-assignment"]');
    expect(error).not.toBeNull();
    expect(error?.textContent).toContain('no in-effect assignment');
    expect(el.querySelector('[data-testid="assignment-pill-summary"]')).toBeNull();
  });

  it('still renders the employee bar in the no-assignment case', () => {
    createWith(makeNoAssignment());
    expect(el.querySelector('[data-testid="employee-display-selected-card"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------
  // countryFlag helper
  // ------------------------------------------------------------------

  it('countryFlag converts ISO codes to emoji and rejects bad input', () => {
    createWith(makeEmployee());
    expect(component.countryFlag('FR')).toBe('🇫🇷');
    expect(component.countryFlag('de')).toBe('🇩🇪'); // case-insensitive
    expect(component.countryFlag('XYZ')).toBe('');
    expect(component.countryFlag('')).toBe('');
    expect(component.countryFlag(null as unknown as string)).toBe('');
  });
});
