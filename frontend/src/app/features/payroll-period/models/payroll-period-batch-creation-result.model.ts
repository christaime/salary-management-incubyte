import { ApiError } from '@core/http/api-error';
import { PayrollPeriodDto } from './payroll-period.model';
import { PayrollPeriodCreateRequest } from './payroll-period-create-request.model';

/** Mirrors PayrollPeriodBatchCreationResult on the backend. */
export interface PayrollPeriodBatchCreationResult {
  succeeded: PayrollPeriodDto[];
  failed: { period: PayrollPeriodCreateRequest; errors: ApiError[] }[];
}


/**
 * Response of GET /api/v1/payroll-periods/generate-sub.
 * `new`   — sub-periods the backend would create (not yet in DB).
 * `exist` — sub-periods already on the chain inside the monthly range.
 */
export interface SubPayrollPeriodCreateResponse {
  newPeriods: PayrollPeriodCreateRequest[];
  exist: PayrollPeriodDto[];
}
