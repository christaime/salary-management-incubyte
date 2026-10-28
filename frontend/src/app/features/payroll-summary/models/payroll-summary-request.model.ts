import { EmploymentType } from '@features/employee/models';

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
  periodId: string;
  anyCountryIds?: string[] | null;
  anyDepartmentIds?: string[] | null;
  anyRoleIds?: string[] | null;
  anyEmploymentTypes?: EmploymentType[] | null;
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
