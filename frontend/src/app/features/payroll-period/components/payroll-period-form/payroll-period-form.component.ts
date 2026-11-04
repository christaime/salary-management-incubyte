import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import {
  PAYROLL_PERIOD_FREQUENCIES,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
  PayrollPeriodFrequency,
} from '@features/payroll-period/models';

export interface PayrollPeriodFormShape {
  name: FormControl<string>;
  frequency: FormControl<PayrollPeriodFrequency>;
  startDate: FormControl<string>;
  endDate: FormControl<string>;
  cascadeToBiWeekly: FormControl<boolean>;
  subPeriodNames: FormArray<FormControl<string>>;
}

export function monthlySpanValidator(
  control: AbstractControl,
): ValidationErrors | null {
  const group = control.parent as FormGroup<PayrollPeriodFormShape> | null;
  if (!group) return null;

  const freq = group.controls.frequency.value;
  if (freq !== 'MONTHLY') return null;

  const start = group.controls.startDate.value;
  const end = control.value as string | null;
  if (!start || !end) return null;

  const spanDays = daysBetweenInclusive(start, end);
  if (spanDays < 0) return null;

  if (spanDays < 28 || spanDays > 31) {
    return { monthlySpan: { spanDays, minSpanDays: 28, maxSpanDays: 31 } };
  }
  return null;
}

function daysBetweenInclusive(startIso: string, endIso: string): number {
  const s = parseIso(startIso);
  const e = parseIso(endIso);
  if (!s || !e) return -1;
  return Math.round((e.getTime() - s.getTime()) / 86_400_000) + 1;
}

function parseIso(iso: string): Date | null {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return null;
  const date = new Date(y, m - 1, d);
  return Number.isNaN(date.getTime()) ? null : date;
}

export interface PreviewRow {
  suggestedName: string | null;
  kind: 'new' | 'existing';
  startDate: string;
  endDate: string;
  editable: boolean;
}

@Component({
  selector: 'app-payroll-period-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './payroll-period-form.component.html',
  styleUrl: './payroll-period-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodFormComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly form = input.required<FormGroup<PayrollPeriodFormShape>>();
  readonly mode = input.required<'new' | 'edit'>();
  readonly saving = input<boolean>(false);
  readonly nameOnly = input<boolean>(false);
  readonly submitBlocked = input<boolean>(false);

  readonly previewRows = input<PreviewRow[]>([]);
  readonly previewLoading = input<boolean>(false);
  readonly previewError = input<string | null>(null);

  readonly submit = output<void>();
  readonly cancel = output<void>();

  readonly frequencies = PAYROLL_PERIOD_FREQUENCIES;
  readonly frequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  private readonly formValid = signal(false);

  readonly canSubmit = computed(
    () => this.formValid() && !this.saving() && !this.submitBlocked(),
  );

  readonly canCascade = computed(
    () =>
      this.mode() === 'new' &&
      this.form().controls.frequency.value === 'MONTHLY',
  );

  readonly isEdit = computed(() => this.mode() === 'edit');

  readonly startDateHint = computed(() => {
    const freq = this.form().controls.frequency.value;
    return freq === 'MONTHLY'
      ? 'Any date. A monthly pay schedule must span 28 to 31 days.'
      : 'Any date. A bi-weekly pay schedule spans exactly 14 days.';
  });

  readonly endDateHint = computed(() =>
    this.form().controls.frequency.value === 'MONTHLY'
      ? 'Between 28 and 31 days after the start date.'
      : 'Exactly 14 days after the start date.',
  );

  constructor() {
    // Drive the disabled state of frequency / startDate / endDate from
    // `nameOnly()`. The template does NOT use [disabled] on these controls —
    // the model does. That removes the
    // "disabled attribute with a reactive form directive" warning and keeps
    // one source of truth for validity.
    effect(() => {
      const blocked = this.nameOnly();
      const group = this.form();
      const targets = [
        group.controls.frequency,
        group.controls.startDate,
        group.controls.endDate,
      ];
      for (const c of targets) {
        if (blocked && c.enabled) {
          c.disable({ emitEvent: false });
        } else if (!blocked && c.disabled) {
          c.enable({ emitEvent: false });
        }
      }
    });
  }

  ngOnInit(): void {
    const group = this.form();
    this.formValid.set(group.valid);
    group.statusChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.formValid.set(group.valid));
  }

  frequencyLabel(value: PayrollPeriodFrequency): string {
    return this.frequencyLabels[value];
  }

  showError(name: 'name' | 'frequency' | 'startDate' | 'endDate'): boolean {
    const c = this.form().controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  errorText(name: 'name' | 'frequency' | 'startDate' | 'endDate'): string | null {
    const c = this.form().controls[name];
    if (!c.invalid || (!c.touched && !c.dirty)) return null;
    if (c.hasError('serverError')) return c.getError('serverError') as string;

    if (name === 'name') {
      if (c.hasError('required')) return 'Name is required.';
      if (c.hasError('minlength')) return 'Name must be at least 2 characters.';
      if (c.hasError('maxlength')) return 'Name must be at most 100 characters.';
    }

    if (name === 'startDate') {
      if (c.hasError('required')) return 'Start date is required.';
    }

    if (name === 'endDate') {
      if (c.hasError('required')) return 'End date is required.';
      const span = c.getError('monthlySpan') as
        | { spanDays: number; minSpanDays: number; maxSpanDays: number }
        | null;
      if (span) {
        return `Span is ${span.spanDays} day${span.spanDays === 1 ? '' : 's'}; `
          + `must be between ${span.minSpanDays} and ${span.maxSpanDays}.`;
      }
    }

    return null;
  }

  nameControl(index: number): FormControl<string> {
    return this.form().controls.subPeriodNames.at(index);
  }

  onSubNameInput(index: number, event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.nameControl(index).setValue(v);
    this.nameControl(index).markAsDirty();
  }
}

// ------------------------------------------------------------------
// Pure helper, exported for tests
// ------------------------------------------------------------------

/**
 * Suggests a default end date for the given start date and frequency.
 *  - MONTHLY, start is the 1st of a calendar month → last day of that month.
 *  - MONTHLY, any other start → start + 27 (28-day span inclusive).
 *  - BI_WEEKLY → start + 13 (14-day span inclusive).
 */
export function computeEndDate(
  startIso: string,
  freq: 'MONTHLY' | 'BI_WEEKLY',
): string {
  if (!startIso) return '';
  const [y, m, d] = startIso.split('-').map(Number);
  const start = new Date(y, (m ?? 1) - 1, d ?? 1);
  if (Number.isNaN(start.getTime())) return '';

  if (freq === 'BI_WEEKLY') {
    const end = new Date(start);
    end.setDate(end.getDate() + 13);
    return toIso(end);
  }

  if (start.getDate() === 1) {
    const lastOfMonth = new Date(start.getFullYear(), start.getMonth() + 1, 0);
    return toIso(lastOfMonth);
  }

  const end = new Date(start);
  end.setDate(end.getDate() + 27);
  return toIso(end);
}

function toIso(d: Date): string {
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}
