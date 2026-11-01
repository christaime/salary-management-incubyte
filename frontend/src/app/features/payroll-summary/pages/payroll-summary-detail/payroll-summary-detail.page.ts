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
  EMPLOYMENT_TYPE_LABELS,
  EmployeeWithInEffectAssignmentDto,
  EmploymentType,
} from '@features/employee/models';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '@core/components/confirm-dialog/confirm-dialog.component';
import { FormEditorError } from '@core/models/form-editor-error.model';

import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryStatusBadgeComponent } from '../../components/payroll-summary-status-badge/payroll-summary-status-badge.component';
import { EmployeeAssignmentDisplayComponent } from '../../components/employee-assignment-display/employee-assignment-display.component';
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
    EmployeeAssignmentDisplayComponent,
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

  readonly listRoute = '/payroll-summaries';
  readonly editorRoute = '/payroll-summaries';

  readonly id = signal<string | null>(null);
  readonly state = signal<PageState>('loading');
  readonly summary = signal<PayrollSummaryDetailDto | null>(null);
  readonly error = signal<FormEditorError | null>(null);

  readonly isDraft = computed(() => this.summary()?.status === 'DRAFT');
  readonly isLoading = computed(() => this.state() === 'loading');

  /**
   * Shape consumed by `app-employee-assignment-display`. Mirrors
   * `PayrollSummaryEditorFormComponent.assignedEmployeeForDisplay` so the
   * detail page renders the exact same employee bar + 7-pill assignment
   * summary as the editor's update/pre-loaded mode.
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

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const id = params.get('id');
        this.id.set(id);
        if (id) this.load(id);
      });
  }

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

  onBack(): void {
    this.router.navigate([this.listRoute]);
  }

  onEdit(): void {
    const s = this.summary();
    if (!s || s.status !== 'DRAFT') return;
    this.router.navigate([this.editorRoute, s.id, 'edit']);
  }

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

  // ---------- Helpers (amounts card only) ----------

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

  computedSum(s: PayrollSummaryDetailDto): number {
    return s.amounts.base + s.amounts.variable + s.amounts.allowances + s.amounts.employerTaxes;
  }

  totalMismatch(s: PayrollSummaryDetailDto): boolean {
    return Math.abs(this.computedSum(s) - s.amounts.total) > 0.01;
  }
}
