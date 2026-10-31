import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { PayrollPeriodCriteria } from '../../../payroll-period/models/payroll-period-criteria.model';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';

import { EMPLOYMENT_TYPES, EmploymentType } from '@features/employee/models';
import { AggregateReportFilterDrawerComponent } from '../aggregate-report-filter-drawer/aggregate-report-filter-drawer.component';
import { RefDto } from '@core/models';

export interface AggregateReportFormValue {
  startYear: number | null;
  startPeriod: PayrollPeriodDto | null;
  endPeriod: PayrollPeriodDto | null;
  filters: {
    countries: RefDto[] | null;
    departments: RefDto[] | null;
    roles: RefDto[] | null;
    employmentTypes: EmploymentType[] | null;
  };
}

@Component({
  selector: 'app-aggregate-report-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatTooltipModule,
    MatIconModule,
    PayrollPeriodPickerComponent,
    AggregateReportFilterDrawerComponent,
  ],
  templateUrl: './aggregate-report-form.component.html',
  styleUrl: './aggregate-report-form.component.scss',
})
export class AggregateReportFormComponent {
  readonly submitting = input<boolean>(false);
  readonly submitForm = output<AggregateReportFormValue>();
  readonly cancel = output<void>();
  readonly formChange = output<AggregateReportFormValue | null>();

  readonly currentYear = new Date().getFullYear();
  readonly employmentTypes: ReadonlyArray<EmploymentType> = EMPLOYMENT_TYPES;

  private readonly fb = new FormBuilder();

  readonly form: FormGroup<{
    startYear: FormControl<number | null>;
    startPeriod: FormControl<PayrollPeriodDto | null>;
    endPeriod: FormControl<PayrollPeriodDto | null>;
    countries: FormControl<RefDto[]>;
    departments: FormControl<RefDto[]>;
    roles: FormControl<RefDto[]>;
    employmentTypes: FormControl<EmploymentType[]>;
  }> = this.fb.group({
    startYear: new FormControl<number | null>(null, [Validators.required]),
    startPeriod: new FormControl<PayrollPeriodDto | null>(null),
    endPeriod: new FormControl<PayrollPeriodDto | null>(null),
    countries: new FormControl<RefDto[]>([], { nonNullable: true }),
    departments: new FormControl<RefDto[]>([], { nonNullable: true }),
    roles: new FormControl<RefDto[]>([], { nonNullable: true }),
    employmentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  readonly startYear = signal<number | null>(null);
  readonly startPeriod = signal<PayrollPeriodDto | null>(null);
  readonly endPeriod = signal<PayrollPeriodDto | null>(null);
  readonly formValid = signal<boolean>(false);
  readonly startPickerSelectedYear = signal<number | null>(null);

  readonly canSubmit = computed(() => (this.startPeriod() !== null || this.startYear() !== null) && !this.submitting());

  readonly startPickerYearRange = computed<[number, number]>(() => [
    this.currentYear - 5,
    this.currentYear,
  ]);

  readonly endPickerSelectedYear = computed<number | null>(() => {
    return this.startPickerSelectedYear() ?? null;
  });

  readonly endPickerYearRange = computed<[number, number]>(() => [
    this.startPickerSelectedYear() ?? (this.currentYear - 5),
    this.currentYear,
  ]);

  readonly periodFilter = signal<PayrollPeriodCriteria>({ frequency: "MONTHLY" });

  readonly endPeriodFilter = computed<PayrollPeriodCriteria>(() => {
    const start = this.startPeriod();
    if (!start) return { frequency: "MONTHLY" };
    return { startDate: start.endDate, frequency: "MONTHLY" };
  });

  constructor() {
    this.form.controls.startYear.valueChanges.subscribe((p) => {
      this.startYear.set(p);
      this.formChange.emit(this.toFormValue());
    });
    this.form.controls.startPeriod.valueChanges.subscribe((p) => {
      this.startPeriod.set(p);

      if (p && p.startDate) {
        this.startPickerSelectedYear.set(Number(p.startDate.slice(0, 4)));
      }

      const end = this.endPeriod();
      if (p && end && p.endDate > end.startDate) {
        this.form.controls.endPeriod.setValue(null);
      }
      this.formChange.emit(this.toFormValue());
    });

    this.form.controls.endPeriod.valueChanges.subscribe((p) => {
      this.endPeriod.set(p);
      this.formChange.emit(this.toFormValue());
    });

    this.form.valueChanges.subscribe(() => {
      this.formChange.emit(this.toFormValue());
    });

    this.form.statusChanges.subscribe(() => {
      this.formValid.set(this.form.valid);
    });
    this.formValid.set(this.form.valid);

    effect(() => {
      const submitting = this.submitting();
      const setDisabled = (c: AbstractControl, d: boolean) => {
        if (d) c.disable({ emitEvent: false });
        else c.enable({ emitEvent: false });
      };
      setDisabled(this.form.controls.startYear, submitting);
      setDisabled(this.form.controls.startPeriod, submitting);
      setDisabled(this.form.controls.endPeriod, submitting);
      setDisabled(this.form.controls.countries, submitting);
      setDisabled(this.form.controls.departments, submitting);
      setDisabled(this.form.controls.roles, submitting);
      setDisabled(this.form.controls.employmentTypes, submitting);
    });
  }

  onStartYearChange(year: number): void {
    this.startPickerSelectedYear.set(year);

    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) < year) {
      this.form.controls.endPeriod.setValue(null);
    }
    this.form.controls.startYear.setValue(year);
  }

  onSubmit(): void {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.toFormValue();
    if (value) this.submitForm.emit(value);
  }

  onReset(): void {
    this.form.reset({
      startYear: null,
      startPeriod: null,
      endPeriod: null,
      countries: [],
      departments: [],
      roles: [],
      employmentTypes: [],
    });
    this.startPickerSelectedYear.set(null);
    this.cancel.emit();
    this.formChange.emit(null);
  }

  private toFormValue(): AggregateReportFormValue | null {
    const v = this.form.getRawValue();
    if (!v.startYear && !v.startPeriod) return null;
    return {
      startYear: v.startYear,
      startPeriod: v.startPeriod,
      endPeriod: v.endPeriod,
      filters: {
        countries: v.countries.length ? [...v.countries] : null,
        departments: v.departments.length ? [...v.departments] : null,
        roles: v.roles.length ? [...v.roles] : null,
        employmentTypes: v.employmentTypes.length ? [...v.employmentTypes] : null,
      },
    };
  }

  onApplyDrawerFilter(){
    this.formChange.emit(this.toFormValue()) ;
  }
}
