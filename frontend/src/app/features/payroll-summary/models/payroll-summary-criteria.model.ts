import { EmploymentType } from '@features/employee/models';
import { PayrollPeriodFrequency } from '@features/payroll-period/models';
import { PayrollSummaryStatus } from './payroll-summary.model';

export interface PayrollSummaryCriteria {
  rangeStart: string;
  rangeEnd: string;
  employeeId?: string | null;
  employeeMatricule?: string | null;
  anyCountryIds?: string[] | null;
  anyDepartmentIds?: string[] | null;
  anyRoleIds?: string[] | null;
  anyEmploymentTypes?: EmploymentType[] | null;
  status?: PayrollSummaryStatus | null;
  payrollFrequency?: PayrollPeriodFrequency | null;
  page: number;
  size: number;
  sortBy?: string | null;
  sortDirection?: 'ASC' | 'DESC';
}
