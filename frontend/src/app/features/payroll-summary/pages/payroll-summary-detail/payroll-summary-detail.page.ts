import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { take } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import {
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  EmploymentType,
} from '@features/employee/models';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '@core/components/confirm-dialog/confirm-dialog.component';
import { FormEditorError } from '@core/models/form-editor-error.model';

import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryStatusBadgeComponent } from '../../components/payroll-summary-status-badge/payroll-summary-status-badge.component';
import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';

type PageState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-payroll-summary-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollSummaryStatusBadgeComponent,
  ],
  templateUrl: './payroll-summary-detail.page.html',
  styleUrl: './payroll-summary-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(PayrollSummaryService);

  // Route constants
  readonly listRoute = '/payroll-summaries';
  readonly editorRoute = '/payroll-summaries';

  // State
  readonly id = signal<string | null>(null);
  readonly state = signal<PageState>('loading');
  readonly summary = signal<PayrollSummaryDetailDto | null>(null);
  readonly error = signal<FormEditorError | null>(null);

  readonly isDraft = computed(() => this.summary()?.status === 'DRAFT');
  readonly isLoading = computed(() => this.state() === 'loading');

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const id = params.get('id');
        this.id.set(id);
        if (id) this.load(id);
      });
  }

  // ---------- Load ----------

  private load(id: string): void {
    this.state.set('loading');
    this.error.set(null);

    this.service
      .getById(id)
      .pipe(take(1))
      .subscribe({
        next: (res) => {
          this.summary.set(res);
          this.state.set('success');
        },
        error: (err) => {
          this.error.set({
            code: err?.code ?? 'UNKNOWN',
            message: err?.message ?? 'Failed to load summary',
            errors: [],
          });
          this.summary.set(null);
          this.state.set('error');
        },
      });
  }

  // ---------- Navigation ----------

  onBack(): void {
    this.router.navigate([this.listRoute]);
  }

  onEdit(): void {
    const s = this.summary();
    if (!s || s.status !== 'DRAFT') return;
    this.router.navigate([this.editorRoute, s.id, 'edit']);
  }

  // ---------- Actions ----------

  onValidate(): void {
    const s = this.summary();
    if (!s || s.status !== 'DRAFT') return;

    const data: ConfirmDialogData = {
      title: 'Validate payroll summary',
      message: `Validate "${s.employee.name}" (${s.period.name})? This is a one-way action and cannot be undone.`,
      confirmLabel: 'Validate',
      cancelLabel: 'Cancel',
      danger: false,
    };

    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
        ConfirmDialogComponent,
        { data },
      )
      .afterClosed()
      .pipe(take(1))
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.service
          .validate(s.id)
          .pipe(take(1))
          .subscribe({
            next: (updated) => this.summary.set(updated),
            error: (err) => {
              this.error.set(err);
              this.state.set('error');
            },
          });
      });
  }

  onDelete(): void {
    const s = this.summary();
    if (!s || s.status !== 'DRAFT') return;

    const data: ConfirmDialogData = {
      title: 'Delete payroll summary',
      message: `Delete the payroll summary for "${s.employee.name}" (${s.period.name})? This cannot be undone.`,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      danger: true,
    };

    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
        ConfirmDialogComponent,
        { data },
      )
      .afterClosed()
      .pipe(take(1))
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.service
          .delete(s.id)
          .pipe(take(1))
          .subscribe({
            next: () => this.router.navigate([this.listRoute]),
            error: (err) => {
              this.error.set(err);
              this.state.set('error');
            },
          });
      });
  }

  // ---------- Helpers ----------

  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1f1e6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }

  employmentTypeLabel(type: EmploymentType): string {
    return EMPLOYMENT_TYPE_LABELS[type];
  }

  formatMoney(amount: number, currency: string): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  formatDate(value: string | null): string {
    if (!value) return 'Open-ended';
    return value;
  }

  /** Recompute the sum of components to compare with `amounts.total`. */
  computedSum(s: PayrollSummaryDetailDto): number {
    return s.amounts.base + s.amounts.variable + s.amounts.allowances + s.amounts.employerTaxes;
  }

  /** True when `amounts.total` doesn't match the sum of components. */
  totalMismatch(s: PayrollSummaryDetailDto): boolean {
    return Math.abs(this.computedSum(s) - s.amounts.total) > 0.01;
  }
}
