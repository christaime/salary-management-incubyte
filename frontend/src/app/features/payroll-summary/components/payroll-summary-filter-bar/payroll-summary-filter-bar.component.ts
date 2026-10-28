import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
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
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import {
  PayrollPeriodCriteria,
  PayrollPeriodDto,
} from '@features/payroll-period/models';
import {
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

export interface PayrollFilterValue {
  rangeStart: string;
  rangeEnd: string;
  status: PayrollSummaryStatus | null;
  payrollFrequency: PayrollPeriodFrequency | null;
  employeeMatricule: string | null;
  anyCountryIds: string[];
  anyDepartmentIds: string[];
  anyRoleIds: string[];
  anyEmploymentTypes: EmploymentType[];
}

interface Option {
  id: string;
  name: string;
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

  readonly currentYear = new Date().getFullYear();

  readonly form = new FormGroup({
    startPeriod: new FormControl<PayrollPeriodDto | null>(null),
    endPeriod: new FormControl<PayrollPeriodDto | null>(null),
    status: new FormControl<PayrollSummaryStatus | null>(null),
    payrollFrequency: new FormControl<PayrollPeriodFrequency | null>(null),
    employeeMatricule: new FormControl<string>('', { nonNullable: true }),
    anyCountryIds: new FormControl<string[]>([], { nonNullable: true }),
    anyDepartmentIds: new FormControl<string[]>([], { nonNullable: true }),
    anyRoleIds: new FormControl<string[]>([], { nonNullable: true }),
    anyEmploymentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  // ---------- Picker state (year + period, tracked separately) ----------

  readonly startPickerSelectedYear = signal<number | null>(null);
  readonly endPickerSelectedYear = signal<number | null>(null);

  readonly startPeriod = signal<PayrollPeriodDto | null>(null);
  readonly endPeriod = signal<PayrollPeriodDto | null>(null);

  // ---------- Picker coordination (mirrors aggregate report) ----------

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

  // ---------- Derived date range (the 4 rules) ----------

  /**
   * True when the user has at least a year or a period in the start picker —
   * i.e. the date range can be computed.
   */
  readonly canComputeRange = computed(
    () => this.startPickerSelectedYear() !== null || this.startPeriod() !== null,
  );

  /**
   * Derives the summary criteria date range from the picker state.
   *
   *   Rule 1: start year only                  → [Y-01-01, Y-12-31]
   *   Rule 2: start period only                → [P.startDate, P.endDate]
   *   Rule 3: start period + end period        → [P.startDate, Q.endDate]
   *   Rule 4: start period + end year only     → [P.startDate, Y'-12-31]
   *   Rule 5: start year + end year only       → [Y-01-01, Y'-12-31]  (natural extension)
   *   Rule 6: start year + end period          → [Y-01-01, Q.endDate] (natural extension)
   */
  readonly derivedRange = computed<{ rangeStart: string; rangeEnd: string } | null>(() => {
    const startPeriod = this.startPeriod();
    const startYear = this.startPickerSelectedYear();
    const endPeriod = this.endPeriod();
    const endYear = this.endPickerSelectedYear();

    // No start info → no range.
    if (!startPeriod && startYear === null) return null;

    // ---- rangeStart ----
    let rangeStart: string;
    if (startPeriod) {
      rangeStart = startPeriod.startDate;
    } else {
      rangeStart = `${startYear}-01-01`;
    }

    // ---- rangeEnd ----
    let rangeEnd: string;
    if (endPeriod) {
      rangeEnd = endPeriod.endDate;
    } else if (endYear !== null) {
      rangeEnd = `${endYear}-12-31`;
    } else if (startPeriod) {
      // Rule 2: no end picker info → use the start period's endDate.
      rangeEnd = startPeriod.endDate;
    } else {
      // Rule 1: no end info at all → use the start year's end.
      rangeEnd = `${startYear}-12-31`;
    }

    return { rangeStart, rangeEnd };
  });

  // ---------- Multi-select trigger labels ----------

  readonly countries = signal<Option[]>([]);
  readonly departments = signal<Option[]>([]);
  readonly roles = signal<Option[]>([]);

  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;

  readonly payrollFrequencies = PAYROLL_PERIOD_FREQUENCIES;
  readonly payrollFrequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  readonly statusOptions: { value: PayrollSummaryStatus | null; label: string }[] = [
    { value: null, label: 'All statuses' },
    { value: 'DRAFT', label: 'Draft' },
    { value: 'VALIDATED', label: 'Validated' },
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
        name: this.employmentTypeLabels[t],
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
        // If the previous end period no longer fits, clear it.
        const end = this.endPeriod();
        if (p && end && p.endDate > end.startDate) {
          this.form.controls.endPeriod.setValue(null, { emitEvent: false });
          this.endPeriod.set(null);
        }
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

  // ---------- Year-change handlers (from the pickers) ----------

  onStartYearChange(year: number): void {
    this.startPickerSelectedYear.set(year);

    // Clear the end period if it now precedes the new start year.
    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) < year) {
      this.form.controls.endPeriod.setValue(null, { emitEvent: false });
      this.endPeriod.set(null);
    }
    // Also clear the end-year if it precedes the new start year.
    const endYear = this.endPickerSelectedYear();
    if (endYear !== null && endYear < year) {
      this.endPickerSelectedYear.set(null);
    }
  }

  onEndYearChange(year: number): void {
    this.endPickerSelectedYear.set(year);
    // If an end period was picked and its year differs from the new year, clear it.
    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) !== year) {
      this.form.controls.endPeriod.setValue(null, { emitEvent: false });
      this.endPeriod.set(null);
    }
  }

  // ---------- Apply / Reset ----------

  onApply(): void {
    const range = this.derivedRange();
    if (!range) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.filterChange.emit({
      rangeStart: range.rangeStart,
      rangeEnd: range.rangeEnd,
      status: v.status,
      payrollFrequency: v.payrollFrequency,
      employeeMatricule: v.employeeMatricule.trim() || null,
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
      employeeMatricule: '',
      anyCountryIds: [],
      anyDepartmentIds: [],
      anyRoleIds: [],
      anyEmploymentTypes: [],
    });
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

  private joinLabels(ids: string[], options: Option[], emptyLabel: string): string {
    if (ids.length === 0) return emptyLabel;
    const names = ids
      .map((id) => options.find((o) => o.id === id)?.name)
      .filter((n): n is string => !!n);
    return names.length > 0 ? names.join(', ') : emptyLabel;
  }

  private loadLookups(): void {
    this.countryService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.countries.set(res.content.map((c) => ({ id: c.id, name: c.name }))),
      );

    this.departmentService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.departments.set(res.content.map((d) => ({ id: d.id, name: d.name }))),
      );

    this.roleService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) =>
        this.roles.set(res.content.map((r) => ({ id: r.id, name: r.name }))),
      );
  }
}
