import { EmploymentType } from '@features/employee/models';
import { PayrollPeriodFrequency } from '@features/payroll-period/models';

export interface PayrollSummaryCreateRequest {
  employeeId: string;
  periodId: string;
  assignmentId: string;
  base: number;
  variable: number;
  allowances: number;
  employerTaxes: number;
  total?: number;
  currency: string;
}

export interface PayrollSummaryUpdateRequest {
  assignmentId: string;
  base: number;
  variable: number;
  allowances: number;
  employerTaxes: number;
  total?: number;
  currency: string;
}

export interface PayrollSummaryValidateRequest {
  rangeStart: string,
  rangeEnd: string,
  anyCountryIds?: string[] | null;
  anyDepartmentIds?: string[] | null;
  anyRoleIds?: string[] | null;
  anyEmploymentTypes?: EmploymentType[] | null;
  payrollFrequency?: PayrollPeriodFrequency | null;
}

export interface ValidateAllFailure {
  summaryId: string;
  code: string;
  message: string;
}

export interface ValidateAllResult {
  total: number;
  validated: number;
  failed: number;
  failures: ValidateAllFailure[];
}
