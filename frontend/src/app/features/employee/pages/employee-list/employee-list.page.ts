import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { take } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { EmployeeService } from '../../services/employee.service';
import { EmployeeFilterBarComponent, EmployeeFilterValue } from '../../components/employee-filter-bar/employee-filter-bar.component';
import { EmployeeTableComponent } from '../../components/employee-table/employee-table.component';
import { PaginatorComponent } from '@core/components/paginator/paginator.component';
import {
  EmployeeCriteria,
  EmployeeWithInEffectAssignmentDto,
} from '../../models';
import {
  DataPage
} from '@core/models';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent, ConfirmDialogData } from '@core/components/confirm-dialog/confirm-dialog.component';
import { PageState } from '@core/models';
import { ToastService } from '../../../../core/ui/toast/toast.service';

@Component({
  selector: 'app-employee-list-page',
  standalone: true,
  imports: [EmployeeFilterBarComponent, EmployeeTableComponent, PaginatorComponent, MatIconModule],
  templateUrl: './employee-list.page.html',
  styleUrl: './employee-list.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeListPage {
  private readonly router = inject(Router);
  private readonly employeeService = inject(EmployeeService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  // Route constants — swap these once the target routes exist.
  readonly newEmployeeRoute = '/employees/new';
  readonly employeeEditRoute = '/employees'; // → `${employeeEditRoute}/${id}`

  // ---------- Pagination + filters ----------

  readonly page = signal(0);
  readonly size = signal(10);
  readonly filters = signal<EmployeeFilterValue>({
    keyword: null,
    assignmentCountryId: null,
    departmentId: null,
    roleId: null,
    employmentType: null,
  });

  // ---------- Response ----------

  readonly state = signal<PageState>('idle');
  readonly result = signal<DataPage<EmployeeWithInEffectAssignmentDto> | null>(null);
  readonly error = signal<string | null>(null);

  readonly isLoading = computed(() => this.state() === 'loading');
  readonly employees = computed(() => this.result()?.content ?? []);
  readonly totalElements = computed(() => this.result()?.totalElements ?? 0);

  // ---------- Handlers ----------

  onFilterChange(next: EmployeeFilterValue): void {
    this.filters.set(next);
    this.page.set(0);
    this.load();
  }

  onPageChange(event: { page: number; size: number }): void {
    this.page.set(event.page);
    this.size.set(event.size);
    this.load();
  }

  onEdit(employee: EmployeeWithInEffectAssignmentDto): void {
    this.router.navigate([this.employeeEditRoute, employee.id]);
  }

  onDelete(employee: EmployeeWithInEffectAssignmentDto): void {
    const data: ConfirmDialogData = {
      title: 'Delete employee',
      message: `Delete "${employee.name}" (${employee.matricule})? This action cannot be undone.`,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      danger: true,
    };

    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
        ConfirmDialogComponent,
        { data, autoFocus: 'dialog', restoreFocus: true },
      )
      .afterClosed()
      .pipe(take(1))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.performDelete(employee);
        }
      });
  }

  private performDelete(employee: EmployeeWithInEffectAssignmentDto): void {
    this.state.set('loading');
    this.error.set(null);

    this.employeeService
      .delete(employee.id)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.toast.success(`Deleted ${employee.name}`);
          this.load();
        },
        error: (err) => {
          this.error.set(err?.message ?? 'Failed to delete employee');
          this.state.set('error');
        },
      });
  }

  onMore(employee: EmployeeWithInEffectAssignmentDto): void {
    // Deferred until the actions menu exists.
    console.info('[employee-list] more requested', employee.id);
  }

  onCreateNew(): void {
    this.router.navigate([this.newEmployeeRoute]);
  }

  // ---------- Initial load ----------

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.error.set(null);

    this.employeeService
      .search(this.buildCriteria())
      .pipe(take(1))
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.state.set('success');
        },
        error: (err) => {
          this.error.set(err?.message ?? 'Failed to load employees');
          this.result.set(null);
          this.state.set('error');
        },
      });
  }

  // ---------- Criteria builder ----------

  private buildCriteria(): EmployeeCriteria {
    const f = this.filters();
    return {
      keyword: f.keyword ?? undefined,
      anyAssignmentCountryIds: f.assignmentCountryId ? [f.assignmentCountryId] : null,
      anyDepartmentIds: f.departmentId ? [f.departmentId] : null,
      anyRoleIds: f.roleId ? [f.roleId] : null,
      anyEmploymentTypes: f.employmentType ? [f.employmentType] : null,
      page: this.page(),
      size: this.size(),
    };
  }
}
