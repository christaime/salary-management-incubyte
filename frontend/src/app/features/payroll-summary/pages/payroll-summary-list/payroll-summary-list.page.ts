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
import { take } from 'rxjs';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { PaginatorComponent } from '@core/components/paginator/paginator.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '@core/components/confirm-dialog/confirm-dialog.component';

import { PayrollSummaryService } from '../../services/payroll-summary.service';
import {
  PayrollSummaryFilterBarComponent,
  PayrollFilterValue,
} from '../../components/payroll-summary-filter-bar/payroll-summary-filter-bar.component';
import { PayrollSummaryTableComponent } from '../../components/payroll-summary-table/payroll-summary-table.component';
import {
  BulkValidateDialogComponent,
  BulkValidateDialogData,
} from '../../components/bulk-validate-dialog/bulk-validate-dialog.component';
import { PayrollSummaryCriteria } from '../../models/payroll-summary-criteria.model';
import { PayrollSummaryDto, PayrollSummaryDetailDto } from '../../models/payroll-summary.model';
import { PayrollPeriodDto } from '@features/payroll-period/models';

type PageState = 'idle' | 'loading' | 'success' | 'error';

@Component({
  selector: 'app-payroll-summary-list-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollSummaryFilterBarComponent,
    PayrollSummaryTableComponent,
    PaginatorComponent,
  ],
  templateUrl: './payroll-summary-list.page.html',
  styleUrl: './payroll-summary-list.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryListPage implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly service = inject(PayrollSummaryService);

  // ---------- Routes ----------
  readonly newSummaryRoute = '/payroll-summaries/new';
  readonly detailRoute = '/payroll-summaries';

  // ---------- State ----------
  readonly state = signal<PageState>('idle');
  readonly page = signal(0);
  readonly size = signal(20);
  readonly totalElements = signal(0);
  readonly rows = signal<PayrollSummaryDto[]>([]);
  readonly error = signal<string | null>(null);
  readonly nonExistentPayroll = signal<boolean>(false);

  private filters: PayrollFilterValue | null = null;

  // ---------- Bulk-validate scope (driven by the start period picker) ----------
  readonly selectedPeriod = signal<PayrollPeriodDto | null>(null);
  readonly canBulkValidate = computed(() => this.selectedPeriod() !== null);

  readonly filterChips = computed<string[]>(() => {
    const f = this.filters;
    if (!f) return [];
    const chips: string[] = [];
    if (f.status) chips.push(`Status: ${f.status}`);
    if (f.payrollFrequency) chips.push(`Frequency: ${f.payrollFrequency}`);
    if (f.employeeId) {
      chips.push(f.employeeName ? `Employee: ${f.employeeName}` : 'Employee: selected');
    }
    if (f.anyCountryIds.length) chips.push(`Countries: ${f.anyCountryIds.length}`);
    if (f.anyDepartmentIds.length) chips.push(`Departments: ${f.anyDepartmentIds.length}`);
    if (f.anyRoleIds.length) chips.push(`Roles: ${f.anyRoleIds.length}`);
    if (f.anyEmploymentTypes.length) chips.push(`Types: ${f.anyEmploymentTypes.length}`);
    return chips;
  });

  readonly bulkValidateLabel = computed(() => {
    const p = this.selectedPeriod();
    if (!p) return 'Validate all drafts';
    const hasChips = this.filterChips().length > 0;
    const suffix = hasChips ? ' corresponding to the criteria' : '';
    return `Validate payrolls on ${p.name}${suffix}`;
  });

  readonly isLoading = computed(() => this.state() === 'loading');

  ngOnInit(): void {
    // No auto search — the user must click Apply on the filter bar.
  }

  // ---------- Filter bar events ----------
  onFilterReset(): void {
    this.state.set('idle');
    this.rows.set([]);
    this.totalElements.set(0);
    this.selectedPeriod.set(null);
    this.nonExistentPayroll.set(false);
  }

  onFilterChange(next: PayrollFilterValue): void {
    this.filters = next;
    this.page.set(0);
    this.nonExistentPayroll.set(next.nonExistent);
    this.service.clearSelectedNonExistent();
    console.log("onFilterChange ",{next});
    this.load();
  }

  onPeriodSelected(period: PayrollPeriodDto | null): void {
    this.selectedPeriod.set(period);
  }

  onPageChange(event: { page: number; size: number }): void {
    this.page.set(event.page);
    this.size.set(event.size);
    this.load();
  }

  // ---------- Load ----------
  private load(): void {
    if (!this.filters) return;

    this.state.set('loading');
    this.error.set(null);

    const criteria: PayrollSummaryCriteria = {
      rangeStart: this.filters.rangeStart,
      rangeEnd: this.filters.rangeEnd,
      status: this.filters.status,
      nonExistent: this.filters.nonExistent,
      payrollFrequency: this.filters.payrollFrequency,
      employeeId: this.filters.employeeId,
      anyCountryIds: this.filters.anyCountryIds,
      anyDepartmentIds: this.filters.anyDepartmentIds,
      anyRoleIds: this.filters.anyRoleIds,
      anyEmploymentTypes: this.filters.anyEmploymentTypes,
      page: this.page(),
      size: this.size(),
    };

    this.service
      .search(criteria)
      .pipe(take(1))
      .subscribe({
        next: (res) => {
          this.rows.set(res.content);
          this.totalElements.set(res.totalElements);
          this.state.set('success');
        },
        error: (err) => {
          this.error.set(err?.message ?? 'Failed to load payroll summaries');
          this.rows.set([]);
          this.totalElements.set(0);
          this.state.set('error');
        },
      });
  }

  // ---------- Row navigation ----------
  onRowClick(row: PayrollSummaryDto): void {
    if (this.filters?.nonExistent) {
      // Non-existent row: there is no detail page to show; go create it.
      this.service.setSelectedNonExistent(row as unknown as PayrollSummaryDetailDto);
      this.router.navigate([this.newSummaryRoute]);
      return;
    }
    this.router.navigate([this.detailRoute, row.id]);
  }

  onEdit(row: PayrollSummaryDto): void {
    if (this.filters?.nonExistent) {
      this.service.setSelectedNonExistent(row as unknown as PayrollSummaryDetailDto);
      this.router.navigate([this.newSummaryRoute]);
      return;
    }
    this.router.navigate([this.detailRoute, row.id, 'edit']);
  }

  onNew(): void {
    this.router.navigate([this.newSummaryRoute]);
  }

  // ---------- Row actions ----------
  onValidate(row: PayrollSummaryDto): void {
    const data: ConfirmDialogData = {
      title: 'Validate payroll summary',
      message: `Validate "${row.employee.name}" (${row.period.name})? This is a one-way action.`,
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
          .validate(row.id)
          .pipe(take(1))
          .subscribe({
            next: () => this.load(),
            error: (err) => this.error.set(err?.message ?? 'Validation failed'),
          });
      });
  }

  onDelete(row: PayrollSummaryDto): void {
    const data: ConfirmDialogData = {
      title: 'Delete payroll summary',
      message: `Delete the payroll summary for "${row.employee.name}" (${row.period.name})? This cannot be undone.`,
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
          .delete(row.id)
          .pipe(take(1))
          .subscribe({
            next: () => this.load(),
            error: (err) => this.error.set(err?.message ?? 'Delete failed'),
          });
      });
  }

  // ---------- Bulk validate ----------
  onBulkValidate(): void {
    const period = this.selectedPeriod();
    if (!period) return;

    const dialogData: BulkValidateDialogData = {
      periodId: period.id,
      periodName: period.name,
      filterChips: this.filterChips(),
    };

    this.dialog
      .open<BulkValidateDialogComponent, BulkValidateDialogData, boolean>(
        BulkValidateDialogComponent,
        { data: dialogData, disableClose: true },
      )
      .afterClosed()
      .pipe(take(1))
      .subscribe((validatedSomething) => {
        if (validatedSomething) this.load();
      });
  }
}
