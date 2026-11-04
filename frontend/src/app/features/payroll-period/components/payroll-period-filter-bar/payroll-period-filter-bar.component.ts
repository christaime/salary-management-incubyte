import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import {
  PAYROLL_PERIOD_FREQUENCIES,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
  PayrollPeriodFrequency,
} from '@features/payroll-period/models';

export type StatusFilter = 'ALL' | 'OPEN' | 'CLOSED';

/** Filter value emitted to the page. Already mapped to API names. */
export interface PayrollPeriodFilterValue {
  keyword: string | null;
  frequency: PayrollPeriodFrequency | null;
  isClosed: boolean | null;
  startDate: string | null; // ISO yyyy-MM-dd
  endDate: string | null;   // ISO yyyy-MM-dd
}

@Component({
  selector: 'app-payroll-period-filter-bar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './payroll-period-filter-bar.component.html',
  styleUrl: './payroll-period-filter-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodFilterBarComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly filterChange = output<PayrollPeriodFilterValue>();
  readonly filterReset = output<void>();

  readonly frequencies = PAYROLL_PERIOD_FREQUENCIES;
  readonly frequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  readonly statusOptions: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'OPEN', label: 'Open' },
    { value: 'CLOSED', label: 'Closed' },
  ];

  readonly form = new FormGroup({
    keyword: new FormControl<string>('', { nonNullable: true }),
    frequency: new FormControl<PayrollPeriodFrequency | null>(null),
    status: new FormControl<StatusFilter>('ALL', { nonNullable: true }),
    startDate: new FormControl<string | null>(null),
    endDate: new FormControl<string | null>(null),
  });

  private readonly startDate = signal<string | null>(null);
  private readonly endDate = signal<string | null>(null);

  readonly canSearch = computed(() => {
    const s = this.startDate();
    const e = this.endDate();
    if (!s || !e) return true; // open bounds are allowed
    return s <= e;             // ISO strings compare lexicographically
  });

  constructor() {
    this.form.controls.startDate.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.startDate.set(v));

    this.form.controls.endDate.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.endDate.set(v));
  }

  onSubmit(): void {
    if (!this.canSearch()) {
      this.form.markAllAsTouched();
      return;
    }
    this.filterChange.emit(this.buildValue());
  }

  onReset(): void {
    this.form.reset({
      keyword: '',
      frequency: null,
      status: 'ALL',
      startDate: null,
      endDate: null,
    });
    this.startDate.set(null);
    this.endDate.set(null);
    this.filterReset.emit();
  }

  frequencyLabel(value: PayrollPeriodFrequency): string {
    return this.frequencyLabels[value];
  }

  private buildValue(): PayrollPeriodFilterValue {
    const v = this.form.getRawValue();

    let isClosed: boolean | null = null;
    if (v.status === 'OPEN') isClosed = false;
    else if (v.status === 'CLOSED') isClosed = true;

    return {
      keyword: v.keyword.trim() ? v.keyword.trim() : null,
      frequency: v.frequency,
      isClosed,
      startDate: v.startDate || null,
      endDate: v.endDate || null,
    };
  }
}
