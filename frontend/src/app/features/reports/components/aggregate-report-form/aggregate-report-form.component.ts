import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ViewChild, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PayrollPeriodPickerComponent } from '../../../payroll-period/components/payroll-period-picker/payroll-period-picker.component';
import { PayrollPeriodCriteria } from '../../../payroll-period/models/payroll-period-criteria.model';
import { PayrollPeriodDto } from '../../../payroll-period/models/payroll-period.model';
import { EmploymentType } from '../../models';
import { AggregateReportFilterDrawerComponent, FilterFormGroup } from '../aggregate-report-filter-drawer/aggregate-report-filter-drawer.component';

export interface AggregateReportFormValue {
  startPeriod: PayrollPeriodDto;
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
    MatButtonModule,
    MatIconModule,
    PayrollPeriodPickerComponent,
    AggregateReportFilterDrawerComponent,
  ],
  templateUrl: './aggregate-report-form.component.html',
  styleUrl: './aggregate-report-form.component.scss',
})
export class AggregateReportFormComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly submitting = input<boolean>(false);

  /** Emits when the user clicks Generate. */
  readonly submitForm = output<AggregateReportFormValue>();
  readonly cancel = output<void>();

  @ViewChild('filterDrawer') readonly filterDrawer!: AggregateReportFilterDrawerComponent;

  readonly form = this.fb.group({
    startPeriod: new FormControl<PayrollPeriodDto | null>(null, [Validators.required]),
    endPeriod: new FormControl<PayrollPeriodDto | null>(null),
    countries: new FormControl<string[]>([], { nonNullable: true }),
    departments: new FormControl<string[]>([], { nonNullable: true }),
    roles: new FormControl<string[]>([], { nonNullable: true }),
    employmentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  get filterFormGroup(): FilterFormGroup {
    return this.form as unknown as FilterFormGroup;
  }

  readonly startPeriod = signal<PayrollPeriodDto | null>(null);
  readonly endPeriod = signal<PayrollPeriodDto | null>(null);

  readonly canSubmit = computed(() => this.startPeriod() !== null && !this.submitting());

  readonly startPeriodFilter = computed<PayrollPeriodCriteria>(() => ({}));

  readonly endPeriodFilter = computed<PayrollPeriodCriteria>(() => {
    const start = this.startPeriod();
    if (!start) return {};
    return { startDate: start.endDate };
  });

  readonly endPickerMinYear = computed<number>(() => {
    const start = this.startPeriod();
    if (!start) return new Date().getFullYear() - 5;
    return Number(start.startDate.slice(0, 4));
  });

  constructor() {
    this.form.controls.startPeriod.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.startPeriod.set(p);
      const end = this.endPeriod();
      if (p && end && p.endDate > end.startDate) {
        this.form.controls.endPeriod.setValue(null);
      }
    });

    this.form.controls.endPeriod.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.endPeriod.set(p);
    });

    effect(() => {
      const submitting = this.submitting();
      const setDisabled = (c: AbstractControl, d: boolean) => {
        if (d) c.disable({ emitEvent: false });
        else c.enable({ emitEvent: false });
      };
      Object.values(this.form.controls).forEach((ctrl) => setDisabled(ctrl, submitting));
    });
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
      startPeriod: null,
      endPeriod: null,
      countries: [],
      departments: [],
      roles: [],
      employmentTypes: [],
    });
    this.cancel.emit();
  }

  private toFormValue(): AggregateReportFormValue | null {
    const v = this.form.getRawValue();
    if (!v.startPeriod) return null;
    return {
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
