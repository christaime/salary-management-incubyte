import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import {
  ConcernedEmployee,
  ReportWarning,
} from '@features/reports/services/reports-error';
import {
  ClosePeriodResponse,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
  PayrollPeriodDto,
  PayrollPeriodFrequency,
} from '@features/payroll-period/models';

export interface PayrollPeriodCloseDialogData {
  id: string;
  name: string;
}

type DialogPhase =
  | 'loading'
  | 'blocked'
  | 'confirm'
  | 'settling'
  | 'done'
  | 'error';

@Component({
  selector: 'app-payroll-period-close-dialog',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './payroll-period-close-dialog.component.html',
  styleUrl: './payroll-period-close-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodCloseDialogComponent implements OnInit {
  private readonly dialogRef =
    inject<MatDialogRef<PayrollPeriodCloseDialogComponent, boolean>>(MatDialogRef);
  private readonly service = inject(PayrollPeriodsService);

  readonly data = inject<PayrollPeriodCloseDialogData>(MAT_DIALOG_DATA);

  readonly phase = signal<DialogPhase>('loading');

  readonly periods = signal<PayrollPeriodDto[]>([]);

  /**
   * Warnings are already mapped to the `ReportWarning` union by
   * PayrollPeriodsService (`normalizeCloseResponse` on the success path,
   * `toPayrollPeriodError` on the error path). Do NOT map them here.
   */
  readonly warnings = signal<ReportWarning[]>([]);

  /** Same contract as `warnings`: already the union, already mapped. */
  readonly blockers = signal<ReportWarning[]>([]);

  readonly errorMessage = signal<string | null>(null);

  readonly acceptClose = signal(false);

  readonly monthly = computed<PayrollPeriodDto | null>(
    () => this.periods().find((p) => p.frequency === 'MONTHLY') ?? null,
  );
  readonly biWeeklies = computed<PayrollPeriodDto[]>(() =>
    this.periods().filter((p) => p.frequency === 'BI_WEEKLY'),
  );
  readonly hasWarnings = computed(() => this.warnings().length > 0);
  readonly canSettle = computed(
    () => this.phase() === 'confirm' && this.acceptClose(),
  );

  readonly primaryLabel = computed(() =>
    this.hasWarnings() ? 'Accept and settle' : 'Settle payroll',
  );

  ngOnInit(): void {
    this.runPreflight();
  }

  // ---------- Actions ----------
  onCancel(): void {
    if (this.phase() === 'settling') return;
    this.dialogRef.close(this.phase() === 'done');
  }

  onAcceptChange(checked: boolean): void {
    this.acceptClose.set(checked);
  }

  onSettle(): void {
    if (!this.canSettle()) return;
    this.phase.set('settling');
    this.errorMessage.set(null);

    this.service.close(this.data.id).subscribe({
      next: (res) => this.applyCloseResult(res),
      error: (err) => this.handleCloseBlockedOrError(err, 'close'),
    });
  }

  onRetryPreflight(): void {
    this.phase.set('loading');
    this.errorMessage.set(null);
    this.acceptClose.set(false);
    this.runPreflight();
  }

  frequencyLabel(value: PayrollPeriodFrequency): string {
    return PAYROLL_PERIOD_FREQUENCY_LABELS[value];
  }

  // ---------- Warning/blocker helpers (read the union shape) ----------
  isEmployeeWarning(w: ReportWarning): boolean {
    return (
      w.code === 'MISSING_PAYROLL_SUMMARY' ||
      w.code === 'UNVALIDATED_PAYROLL_SUMMARY'
    );
  }

  employeeCount(w: ReportWarning): number {
    return this.isEmployeeWarning(w)
      ? (w as { countConcerned?: number }).countConcerned ?? 0
      : 0;
  }

  employees(w: ReportWarning): ConcernedEmployee[] {
    return this.isEmployeeWarning(w)
      ? (w as { employees?: ConcernedEmployee[] }).employees ?? []
      : [];
  }

  moreCount(w: ReportWarning): number {
    const total = this.employeeCount(w);
    const shown = this.employees(w).length;
    return total > shown ? total - shown : 0;
  }

  currency(w: ReportWarning): string | null {
    if (w.code === 'MISSING_FX_RATE' || w.code === 'MISSING_REFERENCE_CURRENCY') {
      const c = (w as { currency?: string }).currency;
      return c ?? null;
    }
    return null;
  }

  // ---------- Internals ----------
  private runPreflight(): void {
    this.service.checkClose(this.data.id).subscribe({
      next: (res) => {
        this.periods.set(res.periods);
        this.warnings.set(res.warnings ?? []);   // already mapped by the service
        this.blockers.set([]);
        this.acceptClose.set(false);
        this.phase.set('confirm');
      },
      error: (err) => this.handleCloseBlockedOrError(err, 'preflight'),
    });
  }

  private applyCloseResult(res: ClosePeriodResponse): void {
    this.periods.set(res.periods);
    this.warnings.set(res.warnings ?? []);       // already mapped by the service
    this.blockers.set([]);
    this.errorMessage.set(null);
    this.phase.set('done');
  }

  /**
   * Errors are already normalized by PayrollPeriodsService
   * (see toPayrollPeriodError). Blockers and warnings are already
   * the `ReportWarning` union. No mapping here.
   */
  private handleCloseBlockedOrError(
    err: unknown,
    source: 'preflight' | 'close',
  ): void {
    const e = err as PayrollPeriodError;

    if (e?.code === 'CLOSE_BLOCKED') {
      this.blockers.set(e.blockers ?? []);
      this.warnings.set(e.warnings ?? []);
      this.periods.set([]);
      this.errorMessage.set(null);
      this.acceptClose.set(false);
      this.phase.set('blocked');
      return;
    }

    this.errorMessage.set(
      e?.message ??
        (source === 'close'
          ? 'Settlement failed'
          : 'Failed to prepare the settlement'),
    );
    this.phase.set('error');
  }
}
