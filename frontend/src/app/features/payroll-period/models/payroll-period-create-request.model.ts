import { PayrollPeriodFrequency } from './payroll-period-frequency.model';

/** Body for POST /api/v1/payroll-periods and PUT /api/v1/payroll-periods/{id}. */
export interface PayrollPeriodCreateRequest {
  name: string;
  startDate: string; // ISO yyyy-MM-dd
  endDate: string;   // ISO yyyy-MM-dd
  frequency: PayrollPeriodFrequency;
}

