import { PayrollPeriodDto } from './payroll-period.model';
import { ReportWarning } from '@features/reports/services/reports-error';

/** Mirrors ClosePeriodResponse on the backend. */
export interface ClosePeriodResponse {
  periods: PayrollPeriodDto[];
  warnings: ReportWarning[];
}
