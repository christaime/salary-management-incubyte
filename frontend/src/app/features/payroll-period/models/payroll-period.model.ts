import { PayrollPeriodFrequency } from './payroll-period-frequency.model';

/**
 * A payroll period returned by `GET /api/v1/payroll-periods`.
 * Mirrors the backend `PayrollPeriodDto`.
 */
export interface PayrollPeriodDto {
  id: string;
  /** Human-readable name, e.g. "September 2026" or "Sept 2026 · W1–W2". */
  name: string;
  /** Inclusive start date, ISO `YYYY-MM-DD`. */
  startDate: string;
  /** Inclusive end date, ISO `YYYY-MM-DD`. */
  endDate: string;
  frequency: PayrollPeriodFrequency;
  closed: boolean;
}
