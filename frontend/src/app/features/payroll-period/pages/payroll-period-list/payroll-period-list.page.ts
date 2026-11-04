import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { take } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { PaginatorComponent } from '@core/components/paginator/paginator.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '@core/components/confirm-dialog/confirm-dialog.component';

import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import {
  PayrollPeriodFilterBarComponent,
  PayrollPeriodFilterValue,
} from '../../components/payroll-period-filter-bar/payroll-period-filter-bar.component';
import { PayrollPeriodTableComponent } from '../../components/payroll-period-table/payroll-period-table.component';
import {
  PayrollPeriodCloseDialogComponent,
  PayrollPeriodCloseDialogData,
} from '../../components/payroll-period-close-dialog/payroll-period-close-dialog.component';
import {
  PayrollPeriodCriteria,
  PayrollPeriodDto,
} from '@features/payroll-period/models';

type PageState = 'loading' | 'success' | 'error';

const EMPTY_FILTER: PayrollPeriodFilterValue = {
  keyword: null,
  frequency: null,
  isClosed: null,
  startDate: null,
  endDate: null,
};

@Component({
  selector: 'app-payroll-period-list-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollPeriodFilterBarComponent,
    PayrollPeriodTableComponent,
    PaginatorComponent,
  ],
  templateUrl: './payroll-period-list.page.html',
  styleUrl: './payroll-period-list.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodListPage implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly service = inject(PayrollPeriodsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly newPeriodRoute = '/payroll-periods/new';

  readonly state = signal<PageState>('loading');
  readonly page = signal(0);
  readonly size = signal(20);
  readonly totalElements = signal(0);
  readonly rows = signal<PayrollPeriodDto[]>([]);
  readonly error = signal<string | null>(null);

  readonly filters = signal<PayrollPeriodFilterValue>({ ...EMPTY_FILTER });

  readonly isLoading = computed(() => this.state() === 'loading');

  ngOnInit(): void {
    this.load();
  }

  onFilterChange(next: PayrollPeriodFilterValue): void {
    this.filters.set(next);
    this.page.set(0);
    this.load();
  }

  onFilterReset(): void {
    this.filters.set({ ...EMPTY_FILTER });
    this.page.set(0);
    this.error.set(null);
    this.load();
  }

  onPageChange(event: { page: number; size: number }): void {
    this.page.set(event.page);
    this.size.set(event.size);
    this.load();
  }

  private load(): void {
    const f = this.filters();

    this.state.set('loading');
    this.error.set(null);

    const criteria: PayrollPeriodCriteria = {
      keyword: f.keyword ?? undefined,
      startDate: f.startDate ?? undefined,
      endDate: f.endDate ?? undefined,
      frequency: f.frequency ?? undefined,
      isClosed: f.isClosed ?? undefined,
      sortBy: 'startDate',
      sortDirection: 'DESC',
      page: this.page(),
      size: this.size(),
    };

    this.service
      .search(criteria)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.rows.set(res.content);
          this.totalElements.set(res.totalElements);
          this.state.set('success');
        },
        error: (err: PayrollPeriodError) => {
          this.error.set(err?.message ?? 'Failed to load pay schedules');
          this.rows.set([]);
          this.totalElements.set(0);
          this.state.set('error');
        },
      });
  }

  onNew(): void {
    this.router.navigate([this.newPeriodRoute]);
  }

  onEdit(row: PayrollPeriodDto): void {
    this.router.navigate(['/payroll-periods', row.id, 'edit']);
  }

  onDelete(row: PayrollPeriodDto): void {
    const data: ConfirmDialogData = {
      title: 'Delete pay schedule',
      message: `Delete "${row.name}"? This cannot be undone.`,
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
            next: () => {
              this.snackBar.open('Pay schedule deleted', 'Dismiss', {
                duration: 3000,
              });
              this.load();
            },
            error: (err: PayrollPeriodError) => {
              this.error.set(this.deleteErrorMessage(err));
              this.state.set('error');
            },
          });
      });
  }

  private deleteErrorMessage(err: PayrollPeriodError): string {
    if (err.code === 'PERIOD_REFERENCED') {
      return 'This pay schedule is used by payroll records and cannot be deleted.';
    }
    if (err.code === 'PERIOD_CLOSED') {
      return 'This pay schedule is already settled and cannot be deleted.';
    }
    return err.message || 'Delete failed';
  }

  onSettle(row: PayrollPeriodDto): void {
    const data: PayrollPeriodCloseDialogData = { id: row.id, name: row.name };

    this.dialog
      .open<
        PayrollPeriodCloseDialogComponent,
        PayrollPeriodCloseDialogData,
        boolean
      >(PayrollPeriodCloseDialogComponent, {
        data,
        disableClose: true,
        width: '900px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(take(1))
      .subscribe((settled) => {
        if (!settled) return;
        this.snackBar.open('Payroll settled', 'Dismiss', { duration: 3000 });
        this.load();
      });
  }
}
