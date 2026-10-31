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
import { RefDto } from '@core/models';

export interface EarningsRankingFormValue {
  period: PayrollPeriodDto | null;
  groupKey: GroupKey | null;
  country: RefDto | null;
  department: RefDto | null;
  role: RefDto | null;
  employmentType: EmploymentType | null;
}

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
  readonly submitForm = output<EarningsRankingFormValue>();
  readonly cancel = output<void>();
  readonly formChange = output<EarningsRankingFormValue | null>();

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
    country: FormControl<RefDto | null>;
    department: FormControl<RefDto | null>;
    role: FormControl<RefDto | null>;
    employmentType: FormControl<EmploymentType | null>;
  }> = new FormGroup({
    period: new FormControl<PayrollPeriodDto | null>(null, [Validators.required]),
    groupKey: new FormControl<GroupKey | null>(null),
    country: new FormControl<RefDto | null>(null),
    department: new FormControl<RefDto | null>(null),
    role: new FormControl<RefDto | null>(null),
    employmentType: new FormControl<EmploymentType | null>(null),
  });

  readonly groupKey = signal<GroupKey | null>(null);
  readonly period = signal<PayrollPeriodDto | null>(null);

  // Lookup data storing full RefDto objects
  readonly countries = signal<RefDto[]>([]);
  readonly departments = signal<RefDto[]>([]);
  readonly roles = signal<RefDto[]>([]);

  readonly pickerYearRange = computed<[number, number]>(() => [
    this.currentYear - 5,
    this.currentYear,
  ]);

  readonly periodFilter = signal<PayrollPeriodCriteria>({ frequency: "MONTHLY" });

  readonly showDimensionPicker = computed(() => this.groupKey() !== null);

  readonly dimensionLabel = computed(() => {
    switch (this.groupKey()) {
      case 'COUNTRY':         return 'Country';
      case 'DEPARTMENT':      return 'Department';
      case 'ROLE':            return 'Role';
      case 'EMPLOYMENT_TYPE': return 'Employment Type';
      default:                return '';
    }
  });

  readonly dimensionControlName = computed<'country' | 'department' | 'role' | 'employmentType' | null>(() => {
    switch (this.groupKey()) {
      case 'COUNTRY':         return 'country';
      case 'DEPARTMENT':      return 'department';
      case 'ROLE':            return 'role';
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
      .subscribe(p => {
        this.period.set(p);
        this.formChange.emit(this.toFormValue());
      });

    this.form.controls.groupKey.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(k => {
        this.onGroupKeyChange(k);
        this.formChange.emit(this.toFormValue());
      });

    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formChange.emit(this.toFormValue());
      });

    effect(() => {
      const isSubmitting = this.submitting();
      Object.values(this.form.controls).forEach(control =>
        this.setDisabled(control, isSubmitting),
      );
    });

    this.loadLookups();
  }

  compareRef(o1: RefDto | null, o2: RefDto | null): boolean {
    if (!o1 || !o2) return o1 === o2;
    return o1.code === o2.code || o1.name === o2.name;
  }

  private loadLookups(): void {
    this.countryService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.countries.set(res.content));

    this.departmentService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.departments.set(res.content));

    this.roleService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.roles.set(res.content));
  }

  private onGroupKeyChange(key: GroupKey | null): void {
    this.form.patchValue({
      country: null,
      department: null,
      role: null,
      employmentType: null,
    }, { emitEvent: false });
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
   const value = this.toFormValue();
   if (value) {
     this.submitForm.emit(value);
   }
 }

  onReset(): void {
    this.form.reset({
      period: null,
      groupKey: null,
      country: null,
      department: null,
      role: null,
      employmentType: null,
    });
    this.groupKey.set(null);
    this.period.set(null);
    this.cancel.emit();
    this.formChange.emit(null);
  }

  private toFormValue(): EarningsRankingFormValue | null {
    const v = this.form.getRawValue();
    if (!v.period) return null;
    return {
      period: v.period,
      groupKey: v.groupKey,
      country: v.country,
      department: v.department,
      role: v.role,
      employmentType: v.employmentType,
    };
  }
}
