import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PayrollSummaryService } from '../../services/payroll-summary.service';
import {
  ValidateAllFailure,
  ValidateAllResult,
} from '../../models/payroll-summary-request.model';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface BulkValidateDialogData {
  periodId: string;
  periodName: string;
  /** Optional summary of the active filters, rendered as small chips. */
  filterChips?: string[];
}

type DialogPhase = 'confirm' | 'running' | 'done' | 'error';

@Component({
  selector: 'app-bulk-validate-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './bulk-validate-dialog.component.html',
  styleUrl: './bulk-validate-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BulkValidateDialogComponent implements OnInit {
  private readonly dialogRef = inject<MatDialogRef<BulkValidateDialogComponent, boolean>>(MatDialogRef);
  private readonly service = inject(PayrollSummaryService);

  readonly data = inject<BulkValidateDialogData>(MAT_DIALOG_DATA);

  readonly phase = signal<DialogPhase>('confirm');
  readonly result = signal<ValidateAllResult | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly failures = computed<ValidateAllFailure[]>(
    () => this.result()?.failures ?? [],
  );

  readonly hasFailures = computed(() => this.failures().length > 0);

  ngOnInit(): void {
    // No prefetch: the user must confirm before anything runs.
  }

  onCancel(): void {
    // Only allow closing before the call starts, or after it completes.
    if (this.phase() === 'running') return;
    this.dialogRef.close(!!this.result());
  }

  onConfirm(): void {
    this.runValidateAll();
  }

  onValidateAgain(): void {
    this.runValidateAll();
  }

  private runValidateAll(): void {
    this.phase.set('running');
    this.errorMessage.set(null);

    this.service.validateAll({ periodId: this.data.periodId }).subscribe({
      next: (res) => {
        this.result.set(res);
        this.phase.set('done');
      },
      error: (err) => {
        this.errorMessage.set(err?.message ?? 'Bulk validation failed');
        this.phase.set('error');
      },
    });
  }

  formatNumber(n: number): string {
    return n.toLocaleString('en-US');
  }
}
