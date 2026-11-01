import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { toSignal, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { startWith } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';
import {
  PayrollSummaryCreateRequest,
  PayrollSummaryUpdateRequest,
} from '../../models/payroll-summary-request.model';
import {
  EMPLOYMENT_TYPE_LABELS,
  EmployeeWithInEffectAssignmentDto,
} from '@features/employee/models';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { PayrollPeriodPickerComponent } from '@features/payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { EmployeePickerComponent } from '../employee-picker/employee-picker.component';
import { EmployeeAssignmentDisplayComponent } from '../employee-assignment-display/employee-assignment-display.component';
import { CountryService } from '@features/country/services/country.service';

export type EditorMode = 'create' | 'update';
export type EditorSavePayload =
  | PayrollSummaryCreateRequest
  | PayrollSummaryUpdateRequest;

type AmountField = 'base' | 'variable' | 'allowances' | 'employerTaxes';

const AMOUNT_FIELDS: readonly AmountField[] = [
  'base',
  'variable',
  'allowances',
  'employerTaxes',
] as const;

@Component({
  selector: 'app-payroll-summary-editor-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    PayrollPeriodPickerComponent,
    EmployeePickerComponent,
    EmployeeAssignmentDisplayComponent,
  ],
  templateUrl: './payroll-summary-editor-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryEditorFormComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);

  readonly mode = input<EditorMode>('update');
  readonly summary = input<PayrollSummaryDetailDto | null>(null);
  readonly saving = input<boolean>(false);
  readonly serverErrors = input<Record<string, string>>({});

  readonly save = output<EditorSavePayload>();
  readonly cancel = output<void>();

  readonly currentYear = new Date().getFullYear();

  readonly form = new FormGroup({
    base: new FormControl<number>(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    variable: new FormControl<number>(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    allowances: new FormControl<number>(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    employerTaxes: new FormControl<number>(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
  });

  /**
   * Signal mirror of the form values. `form.value` is a plain getter, so a
   * `computed` that reads it would never recompute after a silent patch.
   */
  private readonly formValues = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  /**
   * Signal mirror of the form status. Used by `canSave` so it reacts to
   * validity changes (`form.invalid` is a plain getter and would be cached).
   */
  private readonly formStatus = toSignal(
    this.form.statusChanges.pipe(startWith(this.form.status)),
    { initialValue: this.form.status },
  );

  readonly total = computed<number>(() => {
    const v = this.formValues();
    return (v.base ?? 0) + (v.variable ?? 0) + (v.allowances ?? 0) + (v.employerTaxes ?? 0);
  });

  readonly employmentTypeLabel = EMPLOYMENT_TYPE_LABELS;

  readonly amountFields: { key: AmountField; label: string; testid: string }[] = [
    { key: 'base', label: 'Base', testid: 'editor-input-base' },
    { key: 'variable', label: 'Variable', testid: 'editor-input-variable' },
    { key: 'allowances', label: 'Allowances', testid: 'editor-input-allowances' },
    { key: 'employerTaxes', label: 'Employer taxes', testid: 'editor-input-employer-taxes' },
  ];

  // ---------- Create-mode state ----------
  readonly selectedEmployee = signal<EmployeeWithInEffectAssignmentDto | null>(null);
  readonly selectedPeriod = signal<PayrollPeriodDto | null>(null);

  /** Currency resolved from the assignment's country, in create mode. */
  readonly resolvedCurrency = signal<string | null>(null);
  readonly currencyLookupError = signal<string | null>(null);

  private currencyRequestToken = 0;

  /**
   * True when this editor was opened from a non-existent row and the
   * summary carries fixed employee+period data. Amounts are editable;
   * employee and period are locked.
   */
  readonly isPreloaded = computed(
    () => this.mode() === 'create' && this.summary() !== null,
  );

  /**
   * An `EmployeeWithInEffectAssignmentDto` built from the summary DTO,
   * ready for `app-employee-assignment-display`. Used in update mode and
   * in pre-loaded create mode — the summary carries country / department /
   * role / employmentType and the assignment dates, which together are
   * exactly the shape the display component reads.
   */
  readonly assignedEmployeeForDisplay = computed<EmployeeWithInEffectAssignmentDto | null>(() => {
    const s = this.summary();
    if (!s) return null;
    return {
      id: s.employee.id,
      matricule: s.employee.matricule,
      name: s.employee.name,
      email: '',
      birthDate: null,
      originCountry: null,
      assignmentInEffect: {
        id: s.assignment.id,
        startDate: s.assignment.startDate,
        endDate: s.assignment.endDate,
        country: s.country,
        department: s.department,
        role: s.role,
        employmentType: s.employmentType,
        payrollFrequency: s.period.frequency,
        hasPayroll: s.assignment.hasPayroll,
      },
    };
  });

  readonly coverageError = computed<string | null>(() => {
    if (this.mode() !== 'create') return null;
    if (this.isPreloaded()) return null;
    const e = this.selectedEmployee();
    const p = this.selectedPeriod();
    if (!e) return null;
    const a = e.assignmentInEffect;
    if (!a) return 'This employee has no in-effect assignment. Create one before recording payroll.';
    if (!p) return null;
    const covers =
      (!a.startDate || a.startDate <= p.startDate) &&
      (!a.endDate || a.endDate >= p.endDate);
    if (!covers) {
      return "The employee's in-effect assignment does not cover this period. " +
             "Choose a different period or update the employee's assignment.";
    }
    return null;
  });

  readonly currency = computed<string | null>(() => {
    if (this.mode() === 'update') return this.summary()?.amounts.currency ?? null;
    if (this.isPreloaded()) return this.summary()?.amounts.currency ?? null;
    return this.resolvedCurrency();
  });

  readonly isReadOnly = computed(
    () => this.mode() === 'update' && this.summary()?.status === 'VALIDATED',
  );

  readonly canSave = computed(() => {
    if (this.isReadOnly()) return false;
    if (this.formStatus() !== 'VALID') return false;
    if (this.mode() === 'update') return true;
    if (this.isPreloaded()) return true;
    return (
      this.selectedEmployee() !== null &&
      this.selectedEmployee()!.assignmentInEffect !== null &&
      this.selectedPeriod() !== null &&
      this.coverageError() === null
    );
  });

  constructor() {
    effect(() => {
      const s = this.summary();
      if (s && this.mode() === 'update') this.patchFromSummary(s);
    });

    // Reflect server errors onto the controls so Material renders them via
    // <mat-error>. Server errors are not validators, so without this the
    // control stays VALID and <mat-error> never appears.
    effect(() => {
      const serverErrors = this.serverErrors();
      for (const field of AMOUNT_FIELDS) {
        this.applyServerError(field, serverErrors[field] ?? null);
      }
    });
  }

  ngOnInit(): void {
    const s = this.summary();
    if (!s) return;

    if (this.mode() === 'update') {
      this.patchFromSummary(s);
      if (s.status === 'VALIDATED') {
        this.form.disable({ emitEvent: false });
      }
      return;
    }

    if (this.isPreloaded()) {
      this.patchFromSummary(s);
    }
  }

  /**
   * Patches the form from a summary. We deliberately DO NOT pass
   * `emitEvent: false` here so the `formValues` and `formStatus` signals
   * (which drive `total` and `canSave`) recompute. Without this, a summary
   * that arrives after the first render would leave `total` stuck at 0.
   */
  private patchFromSummary(s: PayrollSummaryDetailDto): void {
    this.form.patchValue({
      base: s.amounts.base,
      variable: s.amounts.variable,
      allowances: s.amounts.allowances,
      employerTaxes: s.amounts.employerTaxes,
    });
  }

  /**
   * Sets (or clears) the `server` error on a single control. Preserves any
   * validator errors that may be present. When we set a server error we also
   * mark the control as touched so Material renders <mat-error> without
   * requiring an extra user interaction.
   */
  private applyServerError(field: AmountField, message: string | null): void {
    const ctrl = this.form.controls[field];
    const current = ctrl.errors ?? {};

    if (message) {
      if (current['server'] === message) return; // idempotent
      ctrl.setErrors({ ...current, server: message });
      ctrl.markAsTouched();
      return;
    }

    if (!('server' in current)) return; // nothing to clear
    const { server, ...rest } = current;
    ctrl.setErrors(Object.keys(rest).length ? rest : null);
  }

  onEmployeeSelected(e: EmployeeWithInEffectAssignmentDto | null): void {
    this.selectedEmployee.set(e);
    this.resolvedCurrency.set(null);
    this.currencyLookupError.set(null);

    const countryId = e?.assignmentInEffect?.country?.id;
    if (!countryId) return;

    const token = ++this.currencyRequestToken;

    this.countryService
      .getById(countryId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (country) => {
          if (token !== this.currencyRequestToken) return;
          this.resolvedCurrency.set(country.currencyCode ?? null);
        },
        error: (err) => {
          if (token !== this.currencyRequestToken) return;
          this.currencyLookupError.set(err?.message ?? 'Country lookup failed');
          this.resolvedCurrency.set(null);
        },
      });
  }

  onPeriodSelected(p: PayrollPeriodDto | null): void {
    this.selectedPeriod.set(p);
  }

  onSave(): void {
    if (!this.canSave()) return;
    this.form.markAllAsTouched();

    const v = this.form.getRawValue();
    const total = this.total();

    if (this.mode() === 'update') {
      const s = this.summary();
      if (!s) return;
      const payload: PayrollSummaryUpdateRequest = {
        assignmentId: s.assignment.id,
        base: v.base,
        variable: v.variable,
        allowances: v.allowances,
        employerTaxes: v.employerTaxes,
        total,
        currency: s.amounts.currency,
      };
      this.save.emit(payload);
      return;
    }

    if (this.isPreloaded()) {
      const s = this.summary()!;
      const payload: PayrollSummaryCreateRequest = {
        employeeId: s.employee.id,
        periodId: s.period.id,
        assignmentId: s.assignment.id,
        base: v.base,
        variable: v.variable,
        allowances: v.allowances,
        employerTaxes: v.employerTaxes,
        total,
        currency: s.amounts.currency,
      };
      this.save.emit(payload);
      return;
    }

    const e = this.selectedEmployee();
    const p = this.selectedPeriod();
    if (!e || !p) return;
    const assignment = e.assignmentInEffect;
    if (!assignment || !assignment.id) return;

    const payload: PayrollSummaryCreateRequest = {
      employeeId: e.id,
      periodId: p.id,
      assignmentId: assignment.id,
      base: v.base,
      variable: v.variable,
      allowances: v.allowances,
      employerTaxes: v.employerTaxes,
      total,
      currency: this.resolvedCurrency() ?? '',
    };
    this.save.emit(payload);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  /** Returns the currently rendered error message for a field, if any. */
  errorFor(field: string): string | null {
    const ctrl = this.form.get(field);
    const serverMsg = ctrl?.getError?.('server') as string | undefined;
    if (serverMsg) return serverMsg;
    return this.serverErrors()[field] ?? null;
  }
}
