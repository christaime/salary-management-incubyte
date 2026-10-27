import { DateRange } from './date-range.model';
import { EmploymentType } from '@features/employee/models';
import { GroupKey } from './group-key.model';

/**
 * Request body for `POST /api/v1/reports/aggregate`.
 * Mirrors the backend `AggregateReportRequest` DTO.
 *
 * All `any*` fields are `null` when no filter is applied — never `[]`.
 * The backend treats `null` and `[]` the same, but the frontend always
 * sends `null` for "no filter" so the wire body is unambiguous.
 */
export interface AggregateReportRequest {
  dateRange: DateRange;
  /** At least one key is required. Order is preserved on the wire. */
  groupKeys: GroupKey[];
  anyCountryIds: string[] | null;
  anyDepartmentIds: string[] | null;
  anyRoleIds: string[] | null;
  anyEmploymentTypes: EmploymentType[] | null;
  /** Include a breakdown of base/variable/allowances/taxes per line. */
  includeBreakdown: boolean;
  /** Include min/max/average statistics per line. */
  includeStatistics: boolean;
}
