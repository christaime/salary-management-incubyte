import { RefDto } from '@core/models/reference-data.model';
import { PayrollPeriodFrequency } from '@features/payroll-period/models';
import { EmploymentType } from './employment-type.model';

export interface EmployeeAssignmentDto {
  id: string | null;
  startDate: string;              // ISO YYYY-MM-DD
  endDate: string | null;
  country: RefDto;
  department: RefDto;
  role: RefDto;
  employmentType: EmploymentType;
  payrollFrequency: PayrollPeriodFrequency;
  hasPayroll?: boolean;
}


export interface EmployeeAssignmentSaveRequest {
  id: string | null;             // null on create, ignored on update
  countryId: string;
  departmentId: string;
  roleId: string;
  employmentType: EmploymentType;
  payrollFrequency: PayrollPeriodFrequency;
  startDate: string;             // ISO YYYY-MM-DD
  endDate: string | null;
}

export interface EmployeeAssignmentToCloseRequest {
  id: string;
  endDate: string;
}
