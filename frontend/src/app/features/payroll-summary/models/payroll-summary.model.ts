import { EmployeeRefDto } from '@features/employee/models';
import { EmploymentType } from '@features/employee/models';
import { PayrollPeriodDto } from '@features/payroll-period/models';
import { RefDto } from '@core/models';

export type PayrollSummaryStatus = 'DRAFT' | 'VALIDATED';

/**
 * Amount breakdown in the assignment's currency.
 * Distinct from the aggregate report's `AmountsDto` (which is a different
 * shape on the backend): this one mirrors `AmountsDto` as returned by
 * /api/v1/payroll-summaries.
 */
export interface PayrollSummaryAmountsDto {
  base: number;
  variable: number;
  allowances: number;
  employerTaxes: number;
  total: number;
  currency: string;
}

/**
 * The assignment payload attached to a summary detail.
 * Distinct from `EmployeeAssignmentDto` (which carries denormalized
 * country/department/role RefDto objects). This is the payroll-specific
 * summary of the assignment: id, dates, hasPayroll flag.
 */
export interface PayrollSummaryAssignmentDto {
  id: string;
  startDate: string;
  endDate: string | null;
  hasPayroll: boolean;
}

/** One row of the payroll summary list. */
export interface PayrollSummaryDto {
  id: string;
  status: PayrollSummaryStatus;
  employee: EmployeeRefDto;
  period: PayrollPeriodDto;
  country: RefDto;
  department: RefDto;
  role: RefDto;
  employmentType: EmploymentType;
  amounts: PayrollSummaryAmountsDto;
}

/** Full payload for GET /payroll-summaries/{id}. */
export interface PayrollSummaryDetailDto extends PayrollSummaryDto {
  assignment: PayrollSummaryAssignmentDto;
}
