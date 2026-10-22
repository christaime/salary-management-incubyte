import { PayrollPeriodFrequency } from './payroll-period-frequency.model';

export type SortDirection = 'ASC' | 'DESC';

/**
 * Query parameters for `GET /api/v1/payroll-periods`.
 * Mirrors the fields of the backend `PayrollPeriodCriteria` (without the
 * builder — plain interface on the frontend).
 *
 * All fields are optional; missing fields mean "no filter".
 */
export interface PayrollPeriodCriteria {
  /** Free-text match against the period name. */
  keyword?: string;
  /** Inclusive lower bound on the period start date, ISO `YYYY-MM-DD`. */
  startDate?: string;
  /** Inclusive upper bound on the period end date, ISO `YYYY-MM-DD`. */
  endDate?: string;
  frequency?: PayrollPeriodFrequency;
  isClosed?: boolean;
  /** Field to sort by; default is `startDate`. */
  sortBy?: string;
  /** Sort direction; default is `DESC`. */
  sortDirection?: SortDirection;
  /** Zero-based page index; default is `0`. */
  page?: number;
  /** Page size; default is `20`. */
  size?: number;
}
