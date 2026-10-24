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

import { EMPLOYMENT_TYPES, EmploymentType } from '../../models';
import { AggregateReportFilterDrawerComponent } from '../aggregate-report-filter-drawer/aggregate-report-filter-drawer.component';

export interface AggregateReportFormValue {
  startYear: number | null;
  startPeriod: PayrollPeriodDto | null;
  endPeriod: PayrollPeriodDto | null;
  filters: {
    countryIds: string[] | null;
    departmentIds: string[] | null;
    roleIds: string[] | null;
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
  readonly formChange = output<void>();

  readonly currentYear = new Date().getFullYear();

  readonly employmentTypes: ReadonlyArray<EmploymentType> = EMPLOYMENT_TYPES;

  private readonly fb = new FormBuilder();

  readonly form: FormGroup<{
    startYear: FormControl<number| null>;
    startPeriod: FormControl<PayrollPeriodDto | null>;
    endPeriod: FormControl<PayrollPeriodDto | null>;
    countries: FormControl<string[]>;
    departments: FormControl<string[]>;
    roles: FormControl<string[]>;
    employmentTypes: FormControl<EmploymentType[]>;
  }> = this.fb.group({
    startYear: new FormControl<number | null>(null, [Validators.required]),
    startPeriod: new FormControl<PayrollPeriodDto | null>(null),
    endPeriod: new FormControl<PayrollPeriodDto | null>(null),
    countries: new FormControl<string[]>([], { nonNullable: true }),
    departments: new FormControl<string[]>([], { nonNullable: true }),
    roles: new FormControl<string[]>([], { nonNullable: true }),
    employmentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  readonly startYear = signal<number | null>(null);
  readonly startPeriod = signal<PayrollPeriodDto | null>(null);
  readonly endPeriod = signal<PayrollPeriodDto | null>(null);
  readonly formValid = signal<boolean>(false);

  // Tracks the year selected in the start picker
  readonly startPickerSelectedYear = signal<number | null>(null);

  readonly canSubmit = computed(() => (this.startPeriod() !== null || this.startYear() !== null) && !this.submitting());

  // --- Picker Logic ---

  // Range for the Start picker: [currentYear - 5, currentYear]
  readonly startPickerYearRange = computed<[number, number]>(() => [
    this.currentYear - 5,
    this.currentYear,
  ]);

  // The End picker year is the Start picker year (if set), otherwise the current year
  readonly endPickerSelectedYear = computed<number | null>(() => {
    return this.startPickerSelectedYear() ?? null;
  });

  // Range for the End picker: [startPickerSelectedYear (or currentYear - 5), currentYear]
  readonly endPickerYearRange = computed<[number, number]>(() => [
    this.startPickerSelectedYear() ?? (this.currentYear - 5),
    this.currentYear,
  ]);

  // Filter for the End picker: starts from the endDate of the selected start period
  readonly endPeriodFilter = computed<PayrollPeriodCriteria>(() => {
    const start = this.startPeriod();
    if (!start) return {};
    return { startDate: start.endDate };
  });

  constructor() {
    this.form.controls.startYear.valueChanges.subscribe((p) => {
        this.startYear.set(p);
        this.formChange.emit();
    });
    this.form.controls.startPeriod.valueChanges.subscribe((p) => {
      this.startPeriod.set(p);

      // Sync the startPickerSelectedYear when a period is picked directly
      if (p && p.startDate) {
        this.startPickerSelectedYear.set(Number(p.startDate.slice(0, 4)));
      }

      const end = this.endPeriod();
      if (p && end && p.endDate > end.startDate) {
        this.form.controls.endPeriod.setValue(null);
      }
      this.formChange.emit();
    });

    this.form.controls.endPeriod.valueChanges.subscribe((p) => {
      this.endPeriod.set(p);
      this.formChange.emit();
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

  // Handler for the Start picker's year selection
  onStartYearChange(year: number): void {
    this.startPickerSelectedYear.set(year);

    // Reset end period if it no longer fits the new start year
    const end = this.endPeriod();
    if (end && Number(end.startDate.slice(0, 4)) < year) {
      this.form.controls.endPeriod.setValue(null);
    }
    //  Set the startYear
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
    this.formChange.emit();
  }

  private toFormValue(): AggregateReportFormValue | null {
    const v = this.form.getRawValue();
    if (!v.startYear && !v.startPeriod) return null;
    return {
      startYear: v.startYear,
      startPeriod: v.startPeriod,
      endPeriod: v.endPeriod,
      filters: {
        countryIds: v.countries.length ? [...v.countries] : null,
        departmentIds: v.departments.length ? [...v.departments] : null,
        roleIds: v.roles.length ? [...v.roles] : null,
        employmentTypes: v.employmentTypes.length ? [...v.employmentTypes] : null,
      },
    };
  }
}
