import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { PayrollSummaryEditorFormComponent } from './payroll-summary-editor-form.component';
import { CountryService } from '@features/country/services/country.service';
import { EmployeeWithInEffectAssignmentDto } from '@features/employee/models';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeEmployee(): EmployeeWithInEffectAssignmentDto {
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
  };
}

const JAN_2026: PayrollPeriodDto = {
  id: 'p-1',
  name: 'January 2026',
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  frequency: 'MONTHLY',
  closed: false,
};

function makeSummary(
  overrides: Partial<PayrollSummaryDetailDto> = {},
): PayrollSummaryDetailDto {
  return {
    id: 's-1',
    status: 'DRAFT',
    employee: { id: 'emp-1', matricule: 'EMP-0001', name: 'Ada Lovelace' },
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
    assignment: {
      id: 'a-1',
      startDate: '2025-10-01',
      endDate: null,
      hasPayroll: true,
    } as any,
    ...overrides,
  };
}

describe('PayrollSummaryEditorFormComponent', () => {
  let fixture: ComponentFixture<PayrollSummaryEditorFormComponent>;
  let component: PayrollSummaryEditorFormComponent;
  let el: HTMLElement;
  let country: jasmine.SpyObj<CountryService>;

  function setup(opts: {
    mode?: 'create' | 'update';
    summary?: PayrollSummaryDetailDto | null;
    saving?: boolean;
    serverErrors?: Record<string, string>;
  } = {}): void {
    TestBed.resetTestingModule();
    country = jasmine.createSpyObj('CountryService', ['getById']);
    country.getById.and.returnValue(
      of({ id: 'c-fr', code: 'FR', name: 'France', currencyCode: 'EUR' } as any),
    );

    TestBed.configureTestingModule({
      imports: [PayrollSummaryEditorFormComponent, NoopAnimationsModule],
      providers: [
        { provide: CountryService, useValue: country },
        // Required because EmployeePickerComponent (imported statically by this
        // component) injects EmployeeService → HttpClient.
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    fixture = TestBed.createComponent(PayrollSummaryEditorFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('mode', opts.mode ?? 'create');
    fixture.componentRef.setInput('summary', opts.summary ?? null);
    fixture.componentRef.setInput('saving', opts.saving ?? false);
    fixture.componentRef.setInput('serverErrors', opts.serverErrors ?? {});
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  }

  // ==================================================================
  // Create mode — empty
  // ==================================================================

  describe('create mode (empty)', () => {
    beforeEach(() => setup({ mode: 'create' }));

    it('renders the employee picker and period picker, not the read-only blocks', () => {
      expect(el.querySelector('app-employee-picker')).not.toBeNull();
      expect(el.querySelector('[data-testid="payroll-period-picker"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="editor-period-selected"]')).toBeNull();
    });

    it('does not render the assignment display until an employee is selected', () => {
      expect(el.querySelector('app-employee-assignment-display')).toBeNull();
    });

    it('starts with the amounts form but no resolvable state', () => {
      expect(el.querySelector('[data-testid="editor-input-base"]')).not.toBeNull();
      expect(component.canSave()).toBeFalse();
      expect(component.currency()).toBeNull();
    });

    it('renders the Save button disabled', () => {
      const btn = el.querySelector<HTMLButtonElement>('[data-testid="editor-save"]')!;
      expect(btn.disabled).toBeTrue();
    });

    // ---------- employee + assignment display ----------

    it('shows the assignment display once an employee is selected', () => {
      component.onEmployeeSelected(makeEmployee());
      fixture.detectChanges();
      expect(el.querySelector('app-employee-assignment-display')).not.toBeNull();
    });

    it('resolves currency from the assignment country', () => {
      component.onEmployeeSelected(makeEmployee());
      expect(country.getById).toHaveBeenCalledWith('c-fr');
      expect(component.resolvedCurrency()).toBe('EUR');
    });

    it('sets a lookup error and clears currency when the country lookup fails', () => {
      country.getById.and.returnValue(throwError(() => ({ message: 'lookup failed' })));
      component.onEmployeeSelected(makeEmployee());
      expect(component.currencyLookupError()).toBe('lookup failed');
      expect(component.resolvedCurrency()).toBeNull();
    });

    it('does not look up currency when the employee has no assignment', () => {
      component.onEmployeeSelected({ ...makeEmployee(), assignmentInEffect: null });
      expect(country.getById).not.toHaveBeenCalled();
      expect(component.resolvedCurrency()).toBeNull();
    });

    it('clears the resolved currency when the employee is cleared', () => {
      component.onEmployeeSelected(makeEmployee());
      expect(component.resolvedCurrency()).toBe('EUR');
      component.onEmployeeSelected(null);
      expect(component.resolvedCurrency()).toBeNull();
      expect(component.selectedEmployee()).toBeNull();
    });

    // ---------- coverage ----------

    it('coverageError is null while no employee is selected', () => {
      expect(component.coverageError()).toBeNull();
    });

    it('coverageError flags an employee with no in-effect assignment', () => {
      component.onEmployeeSelected({ ...makeEmployee(), assignmentInEffect: null });
      expect(component.coverageError()).toContain('no in-effect assignment');
    });

    it('coverageError is null when the assignment covers the period', () => {
      component.onEmployeeSelected(makeEmployee());
      component.onPeriodSelected(JAN_2026);
      expect(component.coverageError()).toBeNull();
    });

    it('coverageError flags a period outside the assignment range', () => {
      const e = makeEmployee();
      e.assignmentInEffect = { ...e.assignmentInEffect!, startDate: '2026-06-01' };
      component.onEmployeeSelected(e);
      component.onPeriodSelected(JAN_2026);
      expect(component.coverageError()).toContain('does not cover');
    });

    it('renders the coverage warning banner', () => {
      component.onEmployeeSelected({ ...makeEmployee(), assignmentInEffect: null });
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="coverage-warning"]')).not.toBeNull();
    });

    // ---------- canSave ----------

    it('canSave requires an employee and period with coverage', () => {
      expect(component.canSave()).toBeFalse();

      component.onEmployeeSelected(makeEmployee());
      expect(component.canSave()).toBeFalse();

      component.onPeriodSelected(JAN_2026);
      expect(component.canSave()).toBeTrue();
    });

    it('canSave is false when coverage fails', () => {
      const e = makeEmployee();
      e.assignmentInEffect = { ...e.assignmentInEffect!, startDate: '2026-06-01' };
      component.onEmployeeSelected(e);
      component.onPeriodSelected(JAN_2026);
      expect(component.canSave()).toBeFalse();
    });

    it('renders a currency warning when employee is selected but currency is missing', () => {
      country.getById.and.returnValue(of({ id: 'c-fr', code: 'FR', name: 'France' } as any));
      component.onEmployeeSelected(makeEmployee());
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="currency-warning"]')).not.toBeNull();
    });

    // ---------- total ----------

    it('computes the total from the amount fields', () => {
      component.form.patchValue({
        base: 1000,
        variable: 100,
        allowances: 50,
        employerTaxes: 200,
      });
      expect(component.total()).toBe(1350);
    });

    it('renders the total in the DOM with the currency', () => {
      component.onEmployeeSelected(makeEmployee());
      component.form.patchValue({ base: 10, variable: 20, allowances: 0, employerTaxes: 0 });
      fixture.detectChanges();
      const total = el.querySelector('[data-testid="editor-total"]');
      expect(total?.textContent).toContain('30');
      expect(total?.textContent).toContain('EUR');
      expect(total?.getAttribute('data-value')).toBe('30');
    });

    // ---------- save ----------

    it('emits a create payload with the resolved currency', () => {
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);

      component.onEmployeeSelected(makeEmployee());
      component.onPeriodSelected(JAN_2026);
      component.form.patchValue({ base: 1000, variable: 100, allowances: 50, employerTaxes: 200 });

      component.onSave();

      expect(spy).toHaveBeenCalledOnceWith({
        employeeId: 'emp-1',
        periodId: 'p-1',
        assignmentId: 'a-1',
        base: 1000,
        variable: 100,
        allowances: 50,
        employerTaxes: 200,
        total: 1350,
        currency: 'EUR',
      });
    });

    it('does not emit when cannot save', () => {
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);
      component.onSave();
      expect(spy).not.toHaveBeenCalled();
    });

    it('does not emit when there is no assignment id', () => {
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);
      const e = makeEmployee();
      e.assignmentInEffect = { ...e.assignmentInEffect!, id: '' };
      component.onEmployeeSelected(e);
      component.onPeriodSelected(JAN_2026);
      component.onSave();
      expect(spy).not.toHaveBeenCalled();
    });

    // ---------- cancel ----------

    it('cancel output fires when the cancel button is clicked', () => {
      const spy = jasmine.createSpy('cancel');
      component.cancel.subscribe(spy);
      el.querySelector<HTMLButtonElement>('[data-testid="editor-cancel"]')!.click();
      expect(spy).toHaveBeenCalledTimes(1);
    });

    // ---------- server errors ----------

    it('renders per-field server errors', () => {
      fixture.componentRef.setInput('serverErrors', { base: 'Base is invalid' });
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="editor-error-base"]')?.textContent)
        .toContain('Base is invalid');
    });

    it('clears a per-field server error when serverErrors is emptied', () => {
      fixture.componentRef.setInput('serverErrors', { base: 'Base is invalid' });
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="editor-error-base"]')).not.toBeNull();

      fixture.componentRef.setInput('serverErrors', {});
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="editor-error-base"]')).toBeNull();
      expect(component.form.controls.base.hasError('server')).toBeFalse();
    });

    it('preserves validator errors when clearing the server error', () => {
      component.form.controls.base.setValue(-1);
      expect(component.form.controls.base.hasError('min')).toBeTrue();

      fixture.componentRef.setInput('serverErrors', { base: 'Base is invalid' });
      fixture.detectChanges();
      expect(component.form.controls.base.hasError('server')).toBeTrue();
      expect(component.form.controls.base.hasError('min')).toBeTrue();

      fixture.componentRef.setInput('serverErrors', {});
      fixture.detectChanges();
      expect(component.form.controls.base.hasError('server')).toBeFalse();
      expect(component.form.controls.base.hasError('min')).toBeTrue();
    });

    it('renders the general server error', () => {
      fixture.componentRef.setInput('serverErrors', { __general: 'Save failed' });
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="editor-error-general"]')?.textContent)
        .toContain('Save failed');
    });

    it('errorFor returns null for unknown fields', () => {
      expect(component.errorFor('unknown')).toBeNull();
    });
  });

  // ==================================================================
  // Update mode
  // ==================================================================

  describe('update mode', () => {
    beforeEach(() => setup({ mode: 'update', summary: makeSummary() }));

    it('renders the read-only employee assignment and period pill', () => {
      expect(el.querySelector('app-employee-assignment-display')).not.toBeNull();
      expect(el.querySelector('[data-testid="editor-period-selected"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="period-picker-selected-name"]')?.textContent)
        .toContain('January 2026');
    });

    it('does not render the employee picker or period picker', () => {
      expect(el.querySelector('app-employee-picker')).toBeNull();
      expect(el.querySelector('[data-testid="payroll-period-picker"]')).toBeNull();
    });

    it('patches the amounts from the summary', () => {
      expect(component.form.getRawValue()).toEqual({
        base: 1000,
        variable: 100,
        allowances: 50,
        employerTaxes: 200,
      });
      expect(component.total()).toBe(1350);
    });

    it('uses the summary currency', () => {
      expect(component.currency()).toBe('EUR');
    });

    it('does not flag coverage in update mode', () => {
      expect(component.coverageError()).toBeNull();
    });

    it('canSave is true on a valid DRAFT', () => {
      expect(component.canSave()).toBeTrue();
    });

    it('renders the "Closed" pill when the period is closed', () => {
      setup({
        mode: 'update',
        summary: makeSummary({
          period: { ...makeSummary().period, closed: true },
        }),
      });
      expect(el.querySelector('[data-testid="period-picker-selected-closed"]')).not.toBeNull();
    });

    it('is read-only when the summary is VALIDATED', () => {
      setup({ mode: 'update', summary: makeSummary({ status: 'VALIDATED' }) });
      expect(component.isReadOnly()).toBeTrue();
      expect(component.canSave()).toBeFalse();
      const save = el.querySelector<HTMLButtonElement>('[data-testid="editor-save"]')!;
      expect(save.disabled).toBeTrue();
    });

    it('emits an update payload on save', () => {
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);

      component.form.patchValue({
        base: 2000,
        variable: 200,
        allowances: 100,
        employerTaxes: 400,
      });
      component.onSave();

      expect(spy).toHaveBeenCalledOnceWith({
        assignmentId: 'a-1',
        base: 2000,
        variable: 200,
        allowances: 100,
        employerTaxes: 400,
        total: 2700,
        currency: 'EUR',
      });
    });

    it('does not emit on save when read-only', () => {
      setup({ mode: 'update', summary: makeSummary({ status: 'VALIDATED' }) });
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);
      component.onSave();
      expect(spy).not.toHaveBeenCalled();
    });

    it('canSave is false when the form is invalid', () => {
      component.form.controls.base.setValue(-1);
      fixture.detectChanges();
      expect(component.canSave()).toBeFalse();
    });
  });

  // ==================================================================
  // Pre-loaded create mode (from a non-existent row)
  // ==================================================================

  describe('preloaded create mode', () => {
    beforeEach(() => setup({ mode: 'create', summary: makeSummary() }));

    it('reports isPreloaded and renders the read-only blocks', () => {
      expect(component.isPreloaded()).toBeTrue();
      expect(el.querySelector('app-employee-picker')).toBeNull();
      expect(el.querySelector('[data-testid="editor-period-selected"]')).not.toBeNull();
      expect(el.querySelector('app-employee-assignment-display')).not.toBeNull();
    });

    it('patches the amounts from the summary', () => {
      expect(component.total()).toBe(1350);
      expect(component.currency()).toBe('EUR');
    });

    it('does not flag coverage', () => {
      expect(component.coverageError()).toBeNull();
    });

    it('canSave is true', () => {
      expect(component.canSave()).toBeTrue();
    });

    it('emits a create payload on save', () => {
      const spy = jasmine.createSpy('save');
      component.save.subscribe(spy);

      component.form.patchValue({ base: 500, variable: 0, allowances: 0, employerTaxes: 0 });
      component.onSave();

      expect(spy).toHaveBeenCalledOnceWith({
        employeeId: 'emp-1',
        periodId: 'p-1',
        assignmentId: 'a-1',
        base: 500,
        variable: 0,
        allowances: 0,
        employerTaxes: 0,
        total: 500,
        currency: 'EUR',
      });
    });

    it('does not trigger a currency lookup', () => {
      expect(country.getById).not.toHaveBeenCalled();
      expect(component.currency()).toBe('EUR');
    });
  });

  // ==================================================================
  // assignedEmployeeForDisplay
  // ==================================================================

  describe('assignedEmployeeForDisplay', () => {
    it('returns null when there is no summary', () => {
      setup({ mode: 'create' });
      expect(component.assignedEmployeeForDisplay()).toBeNull();
    });

    it('maps the summary to an EmployeeWithInEffectAssignmentDto', () => {
      setup({ mode: 'update', summary: makeSummary() });
      const e = component.assignedEmployeeForDisplay()!;
      expect(e.id).toBe('emp-1');
      expect(e.matricule).toBe('EMP-0001');
      expect(e.name).toBe('Ada Lovelace');
      expect(e.assignmentInEffect?.country.code).toBe('FR');
      expect(e.assignmentInEffect?.payrollFrequency).toBe('MONTHLY');
      expect(e.assignmentInEffect?.hasPayroll).toBeTrue();
    });
  });

  // ==================================================================
  // Reactivity: effect() picks up a late-arriving summary
  // ==================================================================

  describe('summary input reactivity', () => {
    it('patches the form when a summary arrives after initial render', () => {
      setup({ mode: 'update', summary: null });
      expect(component.total()).toBe(0);

      fixture.componentRef.setInput('summary', makeSummary());
      fixture.detectChanges();

      expect(component.total()).toBe(1350);
    });
  });
});
