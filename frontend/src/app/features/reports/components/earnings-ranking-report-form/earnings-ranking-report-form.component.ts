import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';
import { PayrollPeriodCriteria } from '../../../payroll-period/models/payroll-period-criteria.model';
import { PayrollPeriodFrequency } from '../../../payroll-period/models/payroll-period-frequency.model';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';

import {
  GroupKey,
  EarningRankingReportRequest,
} from '../../models';

import {
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  EmploymentType
} from '@features/employee/models';
type DimensionOption = { id: string; label: string };

@Component({
  selector: 'app-earnings-ranking-report-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    PayrollPeriodPickerComponent,
  ],
  templateUrl: './earnings-ranking-report-form.component.html',
  styleUrl: './earnings-ranking-report-form.component.scss',
})
export class EarningsRankingReportFormComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  readonly submitting = input<boolean>(false);
  readonly submitForm = output<EarningRankingReportRequest>();
  readonly cancel = output<void>();

  readonly currentYear = new Date().getFullYear();

  readonly groupKeys: ReadonlyArray<{ value: GroupKey | null; label: string }> = [
    { value: null,             label: 'Global' },
    { value: 'COUNTRY',        label: 'Per Country' },
    { value: 'DEPARTMENT',     label: 'Per Department' },
    { value: 'ROLE',           label: 'Per Role' },
    { value: 'EMPLOYMENT_TYPE', label: 'Per Employment Type' },
  ];

  readonly employmentTypes: ReadonlyArray<EmploymentType> = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels: Readonly<Record<EmploymentType, string>> = EMPLOYMENT_TYPE_LABELS;

  readonly form: FormGroup<{
    period: FormControl<PayrollPeriodDto | null>;
    groupKey: FormControl<GroupKey | null>;
    countryId: FormControl<string | null>;
    departmentId: FormControl<string | null>;
    roleId: FormControl<string | null>;
    employmentType: FormControl<EmploymentType | null>;
  }> = new FormGroup({
    period: new FormControl<PayrollPeriodDto | null>(null, [Validators.required]),
    groupKey: new FormControl<GroupKey | null>(null),
    countryId: new FormControl<string | null>(null),
    departmentId: new FormControl<string | null>(null),
    roleId: new FormControl<string | null>(null),
    employmentType: new FormControl<EmploymentType | null>(null),
  });

  readonly groupKey = signal<GroupKey | null>(null);
  readonly period = signal<PayrollPeriodDto | null>(null);

  // Lookup data
  readonly countries = signal<DimensionOption[]>([]);
  readonly departments = signal<DimensionOption[]>([]);
  readonly roles = signal<DimensionOption[]>([]);

 // Range for the period picker: [currentYear - 5, currentYear]
  readonly pickerYearRange = computed<[number, number]>(() => [
    this.currentYear - 5,
    this.currentYear,
  ]);

  // Filter for the End picker: starts from the endDate of the selected start period
  readonly periodFilter = signal<PayrollPeriodCriteria>({ frequency: "MONTHLY" });

  /**
   * The dimension selector is only shown when the group key is not null.
   */
  readonly showDimensionPicker = computed(() => this.groupKey() !== null);

  /**
   * Which dimension options the selector should display, based on `groupKey`.
   */
  readonly dimensionOptions = computed<DimensionOption[]>(() => {
    switch (this.groupKey()) {
      case 'COUNTRY':         return this.countries();
      case 'DEPARTMENT':      return this.departments();
      case 'ROLE':            return this.roles();
      case 'EMPLOYMENT_TYPE': return this.employmentTypes.map(t => ({ id: t, label: this.employmentTypeLabels[t] }));
      default:                return [];
    }
  });

  readonly dimensionLabel = computed(() => {
    switch (this.groupKey()) {
      case 'COUNTRY':         return 'Country';
      case 'DEPARTMENT':      return 'Department';
      case 'ROLE':            return 'Role';
      case 'EMPLOYMENT_TYPE': return 'Employment Type';
      default:                return '';
    }
  });

  /**
   * Which form control backs the dimension picker. Used to bind `formControlName`.
   */
  readonly dimensionControlName = computed<'countryId' | 'departmentId' | 'roleId' | 'employmentType' | null>(() => {
    switch (this.groupKey()) {
      case 'COUNTRY':         return 'countryId';
      case 'DEPARTMENT':      return 'departmentId';
      case 'ROLE':            return 'roleId';
      case 'EMPLOYMENT_TYPE': return 'employmentType';
      default:                return null;
    }
  });

  readonly canSubmit = computed(
    () => this.period() !== null && !this.submitting(),
  );

  constructor() {
    this.form.controls.period.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(p => this.period.set(p));

    this.form.controls.groupKey.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(k => this.onGroupKeyChange(k));

    // Disable form when submitting
    effect(() => {
      const isSubmitting = this.submitting();
      Object.values(this.form.controls).forEach(control =>
        this.setDisabled(control, isSubmitting),
      );
    });

    this.loadLookups();
  }

  private loadLookups(): void {
    this.countryService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.countries.set(res.content.map(c => ({ id: c.id, label: c.name }))));

    this.departmentService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.departments.set(res.content.map(d => ({ id: d.id, label: d.name }))));

    this.roleService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.roles.set(res.content.map(r => ({ id: r.id, label: r.name }))));
  }

  private onGroupKeyChange(key: GroupKey | null): void {
    // Reset all dimension controls when the group key changes
    this.form.patchValue({
      countryId: null,
      departmentId: null,
      roleId: null,
      employmentType: null,
    });
    this.groupKey.set(key);
  }

  private setDisabled(control: AbstractControl, disabled: boolean): void {
    if (disabled) {
      control.disable({ emitEvent: false });
    } else {
      control.enable({ emitEvent: false });
    }
  }

  onSubmit(): void {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (!v.period) return;

    const request: EarningRankingReportRequest = {
      periodId: v.period.id,
      groupKey: v.groupKey,
      countryId: v.groupKey === 'COUNTRY' ? v.countryId : null,
      departmentId: v.groupKey === 'DEPARTMENT' ? v.departmentId : null,
      roleId: v.groupKey === 'ROLE' ? v.roleId : null,
      employmentType: v.groupKey === 'EMPLOYMENT_TYPE' ? v.employmentType : null,
    };
    this.submitForm.emit(request);
  }

  onReset(): void {
    this.form.reset({
      period: null,
      groupKey: null,
      countryId: null,
      departmentId: null,
      roleId: null,
      employmentType: null,
    });
    this.groupKey.set(null);
    this.period.set(null);
    this.cancel.emit();
  }
}
