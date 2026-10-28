import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormControl } from '@angular/forms';

import { AssignmentPillComponent } from './assignment-pill.component';
import { EmployeeAssignmentDto } from '../../models';

function makeAssignment(): EmployeeAssignmentDto {
  return {
    id: 'a-1',
    startDate: '2021-04-01',
    endDate: null,
    country: { id: 'c-us', code: 'US', name: 'United States' },
    department: { id: 'd-eng', code: 'ENG', name: 'Engineering' },
    role: { id: 'r-cto', code: 'CTO', name: 'Chief Technology Officer' },
    employmentType: 'FULL_TIME',
    payrollFrequency: 'MONTHLY',
    hasPayroll: true,
  };
}

describe('AssignmentPillComponent', () => {
  let fixture: ComponentFixture<AssignmentPillComponent>;
  let component: AssignmentPillComponent;
  let endDateControl: FormControl<string | null>;

  function setup(
    options: {
      endDate?: string | null;
      endRequired?: boolean;
      newAssignmentActive?: boolean;
      disabled?: boolean;
    } = {},
  ): void {
    endDateControl = new FormControl<string | null>(options.endDate ?? null);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AssignmentPillComponent, NoopAnimationsModule],
    });

    fixture = TestBed.createComponent(AssignmentPillComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('assignment', makeAssignment());
    fixture.componentRef.setInput('endDateControl', endDateControl);
    fixture.componentRef.setInput('endRequired', options.endRequired ?? false);
    fixture.componentRef.setInput('newAssignmentActive', options.newAssignmentActive ?? false);
    fixture.componentRef.setInput('disabled', options.disabled ?? false);
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------
  // Read-only summary
  // ------------------------------------------------------------------

  it('renders country, department, role, employment type, cadence, and start date', () => {
    setup();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-testid="assignment-pill-country-US"]')?.textContent)
      .toContain('United States');
    expect(el.querySelector('[data-testid="assignment-pill-department-ENG"]')?.textContent)
      .toContain('Engineering');
    expect(el.querySelector('[data-testid="assignment-pill-role-CTO"]')?.textContent)
      .toContain('Chief Technology Officer');
    expect(el.querySelector('[data-testid="assignment-pill-employment-type"]')?.textContent)
      .toContain('Full time');
    expect(el.querySelector('[data-testid="assignment-pill-frequency"]')?.textContent)
      .toContain('Monthly');
    expect(el.querySelector('[data-testid="assignment-pill-start"]')?.textContent)
      .toContain('2021-04-01');
  });

  it('renders the flag emoji', () => {
    setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('🇺🇸');
  });

  // ------------------------------------------------------------------
  // End date input
  // ------------------------------------------------------------------

  it('binds the end date to the provided control', () => {
    setup({ endDate: '2026-03-31' });
    const input = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-end-date"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('2026-03-31');
  });

  it('reflects changes from the control back to the input', () => {
    setup({ endDate: null });
    endDateControl.setValue('2026-06-30');
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-end-date"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('2026-06-30');
  });

  it('shows the required marker when endRequired is true', () => {
    setup({ endRequired: true });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('label[for="pill-end-date"]')?.textContent)
      .toContain('*');
  });

  it('hides the required marker when endRequired is false', () => {
    setup({ endRequired: false });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('label[for="pill-end-date"]')?.textContent)
      .not.toContain('*');
  });

  it('shows the "Required" hint when endRequired and no end date', () => {
    setup({ endRequired: true, endDate: null });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="assignment-pill-end-required-hint"]'))
      .not.toBeNull();
  });

  it('hides the "Required" hint when endRequired and end date is set', () => {
    setup({ endRequired: true, endDate: '2026-03-31' });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="assignment-pill-end-required-hint"]'))
      .toBeNull();
  });

  it('hides the "Required" hint when endRequired is false', () => {
    setup({ endRequired: false, endDate: null });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="assignment-pill-end-required-hint"]'))
      .toBeNull();
  });

  // ------------------------------------------------------------------
  // Toggle button
  // ------------------------------------------------------------------

  it('renders "New assignment" when newAssignmentActive is false', () => {
    setup({ newAssignmentActive: false });
    const btn = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-toggle-new"]',
    ) as HTMLButtonElement;
    expect(btn.textContent).toContain('New assignment');
    expect(btn.textContent).not.toContain('Remove');
    expect(btn.getAttribute('data-active')).toBe('false');
  });

  it('renders "Remove new assignment" when newAssignmentActive is true', () => {
    setup({ newAssignmentActive: true });
    const btn = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-toggle-new"]',
    ) as HTMLButtonElement;
    expect(btn.textContent).toContain('Remove new assignment');
    expect(btn.getAttribute('data-active')).toBe('true');
  });

  it('emits toggleNewAssignment when clicked', () => {
    setup();
    const spy = jasmine.createSpy('toggle');
    component.toggleNewAssignment.subscribe(spy);

    const btn = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-toggle-new"]',
    ) as HTMLButtonElement;
    btn.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('does not emit when disabled', () => {
    setup({ disabled: true });
    const spy = jasmine.createSpy('toggle');
    component.toggleNewAssignment.subscribe(spy);

    const btn = fixture.nativeElement.querySelector(
      '[data-testid="assignment-pill-toggle-new"]',
    ) as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    btn.click();

    expect(spy).not.toHaveBeenCalled();
  });
});
