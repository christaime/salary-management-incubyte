import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { LookupOption } from '@core/models';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import {
  PayrollPeriodCriteria,
  PayrollPeriodDto,
} from '@features/payroll-period/models';
import {
  EmployeeWithInEffectAssignmentDto,
  EmploymentType,
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
} from '@features/employee/models';
import {
  PayrollPeriodFrequency,
  PAYROLL_PERIOD_FREQUENCIES,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
} from '@features/payroll-period/models';
import { PayrollSummaryStatus } from '../../models/payroll-summary.model';
import { EmployeePickerComponent } from '../employee-picker/employee-picker.component';

/** UI-only sentinel for the "Non existent" pseudo-status. Never sent to the backend. */
export const STATUS_NON_EXISTENT = '__NON_EXISTENT__' as const;
export type StatusSelectValue = PayrollSummaryStatus | typeof STATUS_NON_EXISTENT | null;

export interface PayrollFilterValue {
  rangeStart: string;
  rangeEnd: string;
  status: PayrollSummaryStatus | null;
  nonExistent: boolean;
  payrollFrequency: PayrollPeriodFrequency | null;
  employeeId: string | null;
  employeeName: string | null;
  anyCountryIds: string[];
  anyDepartmentIds: string[];
  anyRoleIds: string[];
  anyEmploymentTypes: EmploymentType[];
}

@Component({
  selector: 'app-payroll-summary-filter-bar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    PayrollPeriodPickerComponent,
    EmployeePickerComponent,
  ],
  templateUrl: './payroll-summary-filter-bar.component.html',
  styleUrl: './payroll-summary-filter-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryFilterBarComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  readonly filterReset = output<void>();
  readonly filterChange = output<PayrollFilterValue>();
  readonly periodSelected = output<PayrollPeriodDto | null>();

  readonly currentYear = new Date().getFullYear();

  @ViewChild(EmployeePickerComponent) employeePicker?: EmployeePickerComponent;

  readonly form = new FormGroup({
    startPeriod: new FormControl<PayrollPeriodDto | null>(null),
    endPeriod: new FormControl<PayrollPeriodDto | null>(null),
    status: new FormControl<StatusSelectValue>(null),
    payrollFrequency: new FormControl<PayrollPeriodFrequency | null>(null),
    anyCountryIds: new FormControl<string[]>([], { nonNullable: true }),
    anyDepartmentIds: new FormControl<string[]>([], { nonNullable: true }),
    anyRoleIds: new FormControl<string[]>([], { nonNullable: true }),
    anyEmploymentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  // ---------- Employee picker state ----------
  readonly selectedEmployee = signal<EmployeeWithInEffectAssignmentDto | null>(null);

  // ---------- Picker state ----------
  readonly startPickerSelectedYear = signal<number | null>(null);
  readonly endPickerSelectedYear = signal<number | null>(null);

  readonly startPeriod = signal<PayrollPeriodDto | null>(null);
  readonly endPeriod = signal<PayrollPeriodDto | null>(null);

  // ---------- Picker coordination ----------
  readonly startPickerYearRange = computed<[number, number]>(() => [
    this.currentYear - 5,
    this.currentYear,
  ]);

  readonly endPickerYearRange = computed<[number, number]>(() => [
    this.startPickerSelectedYear() ?? (this.currentYear - 5),
    this.currentYear,
  ]);

  readonly endPickerSelectedYearInput = computed<number | null>(
    () => this.startPickerSelectedYear(),
  );

  readonly startPeriodFilter = signal<PayrollPeriodCriteria>({
    frequency: 'MONTHLY',
  });

  readonly endPeriodFilter = computed<PayrollPeriodCriteria>(() => {
    const start = this.startPeriod();
    if (!start) return { frequency: 'MONTHLY' };
    return { startDate: start.endDate, frequency: 'MONTHLY' };
  });

  // ---------- Derived date range ----------
  readonly canComputeRange = computed(
    () => this.startPickerSelectedYear() !== null || this.startPeriod() !== null,
  );

  readonly derivedRange = computed<{ rangeStart: string; rangeEnd: string } | null>(() => {
    const startPeriod = this.startPeriod();
    const startYear = this.startPickerSelectedYear();
    const endPeriod = this.endPeriod();
    const endYear = this.endPickerSelectedYear();

    if (!startPeriod && startYear === null) return null;

    let rangeStart: string;
    if (startPeriod) {
      rangeStart = startPeriod.startDate;
    } else {
      rangeStart = `${startYear}-01-01`;
    }

    let rangeEnd: string;
    if (endPeriod) {
      rangeEnd = endPeriod.endDate;
    } else if (endYear !== null) {
      rangeEnd = `${endYear}-12-31`;
    } else if (startPeriod) {
      rangeEnd = startPeriod.endDate;
    } else {
      rangeEnd = `${startYear}-12-31`;
    }

    return { rangeStart, rangeEnd };
  });

  // ---------- Multi-select option lists (LookupOption) ----------
  readonly countries = signal<LookupOption[]>([]);
  readonly departments = signal<LookupOption[]>([]);
  readonly roles = signal<LookupOption[]>([]);

  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;

  readonly payrollFrequencies = PAYROLL_PERIOD_FREQUENCIES;
  readonly payrollFrequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  /** Status options including the pseudo-status. */
  readonly statusOptions: { value: StatusSelectValue; label: string }[] = [
    { value: null, label: 'All statuses' },
    { value: 'DRAFT', label: 'Draft' },
    { value: 'VALIDATED', label: 'Validated' },
    { value: STATUS_NON_EXISTENT, label: 'Non existent' },
  ];

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  readonly countriesTriggerText = computed(() =>
    this.joinLabels(
      this.formValue()?.anyCountryIds ?? [],
      this.countries(),
      'All jurisdictions',
    ),
  );

  readonly departmentsTriggerText = computed(() =>
    this.joinLabels(
      this.formValue()?.anyDepartmentIds ?? [],
      this.departments(),
      'All departments',
    ),
  );

  readonly rolesTriggerText = computed(() =>
    this.joinLabels(
      this.formValue()?.anyRoleIds ?? [],
      this.roles(),
      'All roles',
    ),
  );

  readonly employmentTypesTriggerText = computed(() =>
    this.joinLabels(
      this.formValue()?.anyEmploymentTypes ?? [],
      this.employmentTypes.map((t) => ({
        id: t,
        label: this.employmentTypeLabels[t],
      })),
      'All types',
    ),
  );

  constructor() {
    this.form.controls.startPeriod.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => {
        this.startPeriod.set(p);
        if (p?.startDate) {
          this.startPickerSelectedYear.set(Number(p.startDate.slice(0, 4)));
        }
        const end = this.endPeriod();
        if (p && end && p.endDate > end.startDate) {
          this.form.controls.endPeriod.setValue(null, { emitEvent: false });
          this.endPeriod.set(null);
        }
        this.periodSelected.emit(p);
      });

    this.form.controls.endPeriod.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => {
        this.endPeriod.set(p);
        if (p?.startDate) {
          this.endPickerSelectedYear.set(Number(p.startDate.slice(0, 4)));
        }
      });
  }

  ngOnInit(): void {
    this.loadLookups();
  }

  // ---------- Year-change handlers ----------
  onStartYearChange(year: number): void {
    this.startPickerSelectedYear.set(year);
    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) < year) {
      this.form.controls.endPeriod.setValue(null, { emitEvent: false });
      this.endPeriod.set(null);
    }
    const endYear = this.endPickerSelectedYear();
    if (endYear !== null && endYear < year) {
      this.endPickerSelectedYear.set(null);
    }
  }

  onEndYearChange(year: number): void {
    this.endPickerSelectedYear.set(year);
    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) !== year) {
      this.form.controls.endPeriod.setValue(null, { emitEvent: false });
      this.endPeriod.set(null);
    }
  }

  // ---------- Employee picker ----------
  onEmployeeSelected(e: EmployeeWithInEffectAssignmentDto | null): void {
    this.selectedEmployee.set(e);
  }

  // ---------- Apply / Reset ----------
  onApply(): void {
    const range = this.derivedRange();
    if (!range) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const emp = this.selectedEmployee();

    const statusSelect = v.status;
    const isNonExistent = statusSelect === STATUS_NON_EXISTENT;
    const status: PayrollSummaryStatus | null =
      statusSelect === 'DRAFT' || statusSelect === 'VALIDATED'
        ? statusSelect
        : null;

    this.filterChange.emit({
      rangeStart: range.rangeStart,
      rangeEnd: range.rangeEnd,
      status,
      nonExistent: isNonExistent,
      payrollFrequency: v.payrollFrequency,
      employeeId: emp?.id ?? null,
      employeeName: emp?.name ?? null,
      anyCountryIds: v.anyCountryIds,
      anyDepartmentIds: v.anyDepartmentIds,
      anyRoleIds: v.anyRoleIds,
      anyEmploymentTypes: v.anyEmploymentTypes,
    });
  }

  onReset(): void {
    this.form.reset({
      startPeriod: null,
      endPeriod: null,
      status: null,
      payrollFrequency: null,
      anyCountryIds: [],
      anyDepartmentIds: [],
      anyRoleIds: [],
      anyEmploymentTypes: [],
    });
    this.selectedEmployee.set(null);
    this.employeePicker?.clearSelection();
    this.startPickerSelectedYear.set(null);
    this.endPickerSelectedYear.set(null);
    this.startPeriod.set(null);
    this.endPeriod.set(null);
    this.filterReset.emit();
  }

  employmentTypeLabel(value: EmploymentType): string {
    return this.employmentTypeLabels[value];
  }

  payrollFrequencyLabel(value: PayrollPeriodFrequency): string {
    return this.payrollFrequencyLabels[value];
  }

  private joinLabels(
    ids: string[],
    options: LookupOption[],
    emptyLabel: string,
  ): string {
    if (ids.length === 0) return emptyLabel;
    const labels = ids
      .map((id) => options.find((o) => o.id === id)?.label)
      .filter((l): l is string => !!l);
    return labels.length > 0 ? labels.join(', ') : emptyLabel;
  }

  private loadLookups(): void {
    this.countryService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.countries.set(res.content.map((c) => ({ id: c.id, label: c.name }))),
      );

    this.departmentService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.departments.set(res.content.map((d) => ({ id: d.id, label: d.name }))),
      );

    this.roleService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.roles.set(res.content.map((r) => ({ id: r.id, label: r.name }))),
      );
  }
}
