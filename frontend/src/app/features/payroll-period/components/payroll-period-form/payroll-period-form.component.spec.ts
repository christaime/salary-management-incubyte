import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  FormArray,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';

import {
  PayrollPeriodFormComponent,
  PayrollPeriodFormShape,
  PreviewRow,
  computeEndDate,
  monthlySpanValidator,
} from './payroll-period-form.component';

describe('PayrollPeriodFormComponent', () => {
  // ---------- fixtures ----------
  function buildForm(
    overrides: Partial<PayrollPeriodFormShape> = {},
  ): FormGroup<PayrollPeriodFormShape> {
    return new FormGroup<PayrollPeriodFormShape>({
      name:
        overrides.name ??
        new FormControl('', {
          nonNullable: true,
          validators: [
            Validators.required,
            Validators.minLength(2),
            Validators.maxLength(100),
          ],
        }),
      frequency: overrides.frequency ?? new FormControl<'MONTHLY' | 'BI_WEEKLY'>('MONTHLY', { nonNullable: true }),
      startDate:
        overrides.startDate ??
        new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      endDate:
        overrides.endDate ??
        new FormControl('', {
          nonNullable: true,
          validators: [Validators.required, monthlySpanValidator],
        }),
      cascadeToBiWeekly: overrides.cascadeToBiWeekly ?? new FormControl(false, { nonNullable: true }),
      subPeriodNames: overrides.subPeriodNames ?? new FormArray<FormControl<string>>([]),
    });
  }

  let fixture: ComponentFixture<PayrollPeriodFormComponent>;
  let component: PayrollPeriodFormComponent;
  let form: FormGroup<PayrollPeriodFormShape>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PayrollPeriodFormComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PayrollPeriodFormComponent);
    component = fixture.componentInstance;

    form = buildForm();
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('mode', 'new');
  });

  // ---------- helpers ----------
  function detect(): void {
    fixture.detectChanges();
  }

  function byId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  // ==================================================================
  // Behaviour — computeEndDate (pure function)
  // ==================================================================

  describe('computeEndDate', () => {
    it('MONTHLY on the 1st → last day of that calendar month', () => {
      expect(computeEndDate('2026-05-01', 'MONTHLY')).toBe('2026-05-31');
      expect(computeEndDate('2026-04-01', 'MONTHLY')).toBe('2026-04-30');
      expect(computeEndDate('2026-02-01', 'MONTHLY')).toBe('2026-02-28');
    });

    it('MONTHLY on the 1st of February in a leap year → Feb 29', () => {
      expect(computeEndDate('2024-02-01', 'MONTHLY')).toBe('2024-02-29');
    });

    it('MONTHLY on any other day → +27 days (28-day span)', () => {
      expect(computeEndDate('2026-05-03', 'MONTHLY')).toBe('2026-05-30');
      expect(computeEndDate('2026-05-15', 'MONTHLY')).toBe('2026-06-11');
    });

    it('BI_WEEKLY → +13 days (14-day span) regardless of the day', () => {
      expect(computeEndDate('2026-05-01', 'BI_WEEKLY')).toBe('2026-05-14');
      expect(computeEndDate('2026-05-03', 'BI_WEEKLY')).toBe('2026-05-16');
    });

    it('empty start → empty string', () => {
      expect(computeEndDate('', 'MONTHLY')).toBe('');
      expect(computeEndDate('', 'BI_WEEKLY')).toBe('');
    });
  });

  // ==================================================================
  // Behaviour — monthlySpanValidator
  // ==================================================================

  describe('monthlySpanValidator', () => {
    function controlWith(
      start: string,
      end: string,
      freq: 'MONTHLY' | 'BI_WEEKLY' = 'MONTHLY',
    ): FormControl<string> {
      const parent = buildForm();
      parent.controls.frequency.setValue(freq);
      parent.controls.startDate.setValue(start);
      const ctrl = parent.controls.endDate;
      ctrl.setValue(end);
      return ctrl;
    }

    it('accepts 28 days inclusive', () => {
      const c = controlWith('2026-05-01', '2026-05-28');
      expect(monthlySpanValidator(c)).toBeNull();
    });

    it('accepts 31 days inclusive', () => {
      const c = controlWith('2026-05-01', '2026-05-31');
      expect(monthlySpanValidator(c)).toBeNull();
    });

    it('rejects 27 days with the span metadata', () => {
      const c = controlWith('2026-05-01', '2026-05-27');
      const err = monthlySpanValidator(c);
      expect(err).toEqual({
        monthlySpan: { spanDays: 27, minSpanDays: 28, maxSpanDays: 31 },
      });
    });

    it('rejects 32 days', () => {
      const c = controlWith('2026-05-01', '2026-06-01');
      expect(monthlySpanValidator(c)).toEqual({
        monthlySpan: { spanDays: 32, minSpanDays: 28, maxSpanDays: 31 },
      });
    });

    it('returns null for BI_WEEKLY regardless of the span', () => {
      const c = controlWith('2026-05-01', '2026-06-30', 'BI_WEEKLY');
      expect(monthlySpanValidator(c)).toBeNull();
    });

    it('returns null when start > end (DateOrder is handled elsewhere)', () => {
      const c = controlWith('2026-05-15', '2026-05-01');
      expect(monthlySpanValidator(c)).toBeNull();
    });
  });

  // ==================================================================
  // Behaviour — formValid / canSubmit
  // ==================================================================

  describe('formValid and canSubmit', () => {
    it('reflects the form status after ngOnInit', () => {
      detect();
      expect(component['formValid']()).toBeFalse();

      form.controls.name.setValue('May 2026');
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');
      expect(form.valid).toBeTrue();
      expect(component['formValid']()).toBeTrue();
    });

    it('canSubmit = formValid && !saving && !submitBlocked', () => {
      detect();
      form.controls.name.setValue('May 2026');
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');

      expect(component.canSubmit()).toBeTrue();

      fixture.componentRef.setInput('saving', true);
      detect();
      expect(component.canSubmit()).toBeFalse();

      fixture.componentRef.setInput('saving', false);
      fixture.componentRef.setInput('submitBlocked', true);
      detect();
      expect(component.canSubmit()).toBeFalse();
    });

    it('canSubmit becomes false when the form becomes invalid', () => {
      detect();
      form.controls.name.setValue('May 2026');
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');
      expect(component.canSubmit()).toBeTrue();

      form.controls.endDate.setValue('2026-05-20'); // 20-day span → invalid
      expect(component.canSubmit()).toBeFalse();
    });
  });

  // ==================================================================
  // Behaviour — showError / errorText
  // ==================================================================

  describe('errorText', () => {
    beforeEach(() => detect());

    it('name: required, minlength, maxlength, then serverError wins', () => {
      const c = form.controls.name;
      c.markAsTouched();
      expect(component.errorText('name')).toBe('Name is required.');

      c.setValue('X');
      expect(component.errorText('name')).toBe('Name must be at least 2 characters.');

      c.setValue('x'.repeat(101));
      expect(component.errorText('name')).toBe('Name must be at most 100 characters.');

      c.setErrors({ serverError: 'Name already exists' });
      expect(component.errorText('name')).toBe('Name already exists');
    });

    it('startDate: required', () => {
      form.controls.startDate.markAsTouched();
      expect(component.errorText('startDate')).toBe('Start date is required.');
    });

    it('endDate: required, then monthlySpan with human span wording', () => {
      const c = form.controls.endDate;
      c.markAsTouched();
      expect(component.errorText('endDate')).toBe('End date is required.');

      form.controls.startDate.setValue('2026-05-01');
      c.setValue('2026-05-20'); // 20 days
      expect(component.errorText('endDate')).toBe(
        'Span is 20 days; must be between 28 and 31.',
      );

      c.setValue('2026-05-21'); // 21 days
      expect(component.errorText('endDate')).toContain('21 days');
    });

    it('showError respects touched-or-dirty', () => {
      const c = form.controls.name;
      expect(component.showError('name')).toBeFalse();

      c.markAsTouched();
      expect(component.showError('name')).toBeTrue();

      c.markAsUntouched();
      c.markAsDirty();
      expect(component.showError('name')).toBeTrue();
    });
  });

  // ==================================================================
  // Behaviour — onSubNameInput
  // ==================================================================

  describe('onSubNameInput', () => {
    beforeEach(() => detect());

    it('writes into the corresponding FormControl and marks it dirty', () => {
      form.controls.subPeriodNames.push(new FormControl('BW1 May 2026', { nonNullable: true }));
      form.controls.subPeriodNames.push(new FormControl('BW2 May 2026', { nonNullable: true }));

      const event = { target: { value: 'BW1 renamed' } } as unknown as Event;
      component.onSubNameInput(0, event);

      expect(form.controls.subPeriodNames.at(0).value).toBe('BW1 renamed');
      expect(form.controls.subPeriodNames.at(0).dirty).toBeTrue();
      expect(form.controls.subPeriodNames.at(1).value).toBe('BW2 May 2026');
    });
  });

  // ==================================================================
  // Template — field errors and disabled state
  // ==================================================================

  describe('template — fields and button', () => {
    beforeEach(() => detect());

    it('shows the name error under the input after touch', () => {
      form.controls.name.markAsTouched();
      detect();

      expect(byId('form-name-error')!.textContent).toContain('Name is required.');
    });

    it('shows the end-date monthlySpan error after touch', () => {
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-20');
      form.controls.endDate.markAsTouched();
      detect();

      expect(byId('form-end-date-error')!.textContent).toContain('Span is 20 days');
    });

    it('primary button is disabled while the form is invalid', () => {
      const btn = byId('form-submit') as HTMLButtonElement;
      expect(btn.disabled).toBeTrue();

      form.controls.name.setValue('May 2026');
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');
      detect();

      expect((byId('form-submit') as HTMLButtonElement).disabled).toBeFalse();
    });

    it('primary button label reflects the mode', () => {
      form.controls.name.setValue('May 2026');
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');
      detect();

      expect((byId('form-submit') as HTMLButtonElement).textContent).toContain(
        'Create pay schedule',
      );

      fixture.componentRef.setInput('mode', 'edit');
      detect();
      expect((byId('form-submit') as HTMLButtonElement).textContent).toContain(
        'Save changes',
      );
    });

    it('Cancel is enabled unless saving', () => {
      expect((byId('form-cancel') as HTMLButtonElement).disabled).toBeFalse();

      fixture.componentRef.setInput('saving', true);
      detect();
      expect((byId('form-cancel') as HTMLButtonElement).disabled).toBeTrue();
    });
  });

  // ==================================================================
  // Template — cascade visibility rules
  // ==================================================================

  describe('template — cascade visibility', () => {
    it('is hidden in edit mode', () => {
      fixture.componentRef.setInput('mode', 'edit');
      detect();
      expect(byId('form-cascade')).toBeNull();
    });

    it('is hidden for BI_WEEKLY', () => {
      form.controls.frequency.setValue('BI_WEEKLY');
      detect();
      expect(byId('form-cascade')).toBeNull();
    });

    it('is visible in new mode with MONTHLY', () => {
      detect();
      expect(byId('form-cascade')).toBeTruthy();
    });

    it('preview renders only when cascadeToBiWeekly is true', () => {
      detect();
      expect(byId('form-cascade-preview')).toBeNull();

      form.controls.cascadeToBiWeekly.setValue(true);
      detect();
      expect(byId('form-cascade-preview')).toBeTruthy();
    });
  });

  // ==================================================================
  // Template — cascade preview rows
  // ==================================================================

  describe('template — cascade preview', () => {
    const NEW_ROW: PreviewRow = {
      suggestedName: 'BW1 May 2026',
      kind: 'new',
      startDate: '2026-05-01',
      endDate: '2026-05-14',
      editable: true,
    };
    const EXISTING_ROW: PreviewRow = {
      suggestedName: 'BW1 Apr 2026',
      kind: 'existing',
      startDate: '2026-04-01',
      endDate: '2026-04-14',
      editable: false,
    };

    function setupPreview(): void {
      form.controls.cascadeToBiWeekly.setValue(true);
      form.controls.startDate.setValue('2026-05-01');
      form.controls.endDate.setValue('2026-05-28');
      form.controls.subPeriodNames.push(
        new FormControl('BW1 Apr 2026', { nonNullable: true }),
      );
      form.controls.subPeriodNames.push(
        new FormControl('BW1 May 2026', { nonNullable: true }),
      );

      fixture.componentRef.setInput('previewRows', [EXISTING_ROW, NEW_ROW]);
      detect();
    }

    it('shows the loading label when previewLoading is true', () => {
      setupPreview();
      fixture.componentRef.setInput('previewLoading', true);
      detect();
      expect(byId('form-cascade-preview')!.textContent).toContain('Loading…');
    });

    it('shows the error text when previewError is set', () => {
      setupPreview();
      fixture.componentRef.setInput('previewError', 'Preview failed');
      detect();
      expect(byId('form-cascade-preview-error')!.textContent).toContain('Preview failed');
    });

    it('shows the empty branch when there are no rows and no error', () => {
      form.controls.cascadeToBiWeekly.setValue(true);
      fixture.componentRef.setInput('previewRows', []);
      detect();
      expect(byId('form-cascade-preview-empty')).toBeTruthy();
    });

    it('renders existing rows as plain text and new rows as editable inputs', () => {
      setupPreview();

      const existing = fixture.nativeElement.querySelector(
        `[data-testid="form-cascade-name-0"]`,
      ) as HTMLInputElement | null;
      const newInput = fixture.nativeElement.querySelector(
        `[data-testid="form-cascade-name-1"]`,
      ) as HTMLInputElement | null;

      expect(existing).toBeNull();   // existing rows are text, not inputs
      expect(newInput).toBeTruthy();
      expect(newInput!.value).toBe('BW1 May 2026');
    });

    it('editing a new row updates subPeriodNames at the right index', () => {
      setupPreview();

      const input = fixture.nativeElement.querySelector(
        '[data-testid="form-cascade-name-1"]',
      ) as HTMLInputElement;
      input.value = 'BW1 May 2026 renamed';
      input.dispatchEvent(new Event('input'));

      expect(form.controls.subPeriodNames.at(1).value).toBe(
        'BW1 May 2026 renamed',
      );
    });

    it('renders the New / Existing status chips per row', () => {
      setupPreview();
      const text = fixture.nativeElement.textContent as string;
      // Two rows present, both chips rendered.
      expect(text).toContain('Existing');
      expect(text).toContain('New');
    });
  });
});
