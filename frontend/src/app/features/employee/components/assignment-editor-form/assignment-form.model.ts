import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  EmploymentType,
} from '@features/employee/models';
import {
  PayrollPeriodFrequency,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
} from '@features/payroll-period/models';

export interface AssignmentEditorForm {
  employmentType: FormControl<EmploymentType | null>;
  departmentId: FormControl<string | null>;
  roleId: FormControl<string | null>;
  assignmentCountryId: FormControl<string | null>;
  payrollFrequency: FormControl<PayrollPeriodFrequency | null>;
  startDate: FormControl<string | null>;
  endDate: FormControl<string | null>;
}

export interface AssignmentEditorValue {
  employmentType: EmploymentType | null;
  departmentId: string | null;
  roleId: string | null;
  assignmentCountryId: string | null;
  payrollFrequency: PayrollPeriodFrequency | null;
  startDate: string | null;
  endDate: string | null;
}

/** endDate must be null or strictly after startDate. */
export const endDateAfterStartValidator: ValidatorFn = (
  group: AbstractControl,
): ValidationErrors | null => {
  const start = group.get('startDate')?.value as string | null;
  const end = group.get('endDate')?.value as string | null;
  if (!start || !end) return null;
  return new Date(end) > new Date(start) ? null : { endBeforeStart: true };
};

export function createAssignmentEditorForm(): FormGroup<AssignmentEditorForm> {
  const group = new FormGroup<AssignmentEditorForm>({
    employmentType: new FormControl<EmploymentType | null>(null, {
      validators: [Validators.required],
    }),
    departmentId: new FormControl<string | null>(null, {
      validators: [Validators.required],
    }),
    roleId: new FormControl<string | null>(null, {
      validators: [Validators.required],
    }),
    assignmentCountryId: new FormControl<string | null>(null, {
      validators: [Validators.required],
    }),
    payrollFrequency: new FormControl<PayrollPeriodFrequency | null>(null, {
      validators: [Validators.required],
    }),
    startDate: new FormControl<string | null>(null, {
      validators: [Validators.required],
    }),
    endDate: new FormControl<string | null>(null),
  });

  group.setValidators(endDateAfterStartValidator);
  return group;
}

// Re-export the labels so the child component and the pill can share them.
export { EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_LABELS, PAYROLL_PERIOD_FREQUENCY_LABELS };
export type { EmploymentType, PayrollPeriodFrequency };
