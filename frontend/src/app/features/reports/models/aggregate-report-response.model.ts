import { AggregateLineDto } from './aggregate-line.model';
import { DateRange } from './date-range.model';
import { GroupKey } from './group-key.model';
import { ReportWarningDto } from './report-warning.model';

/**
 * Response body for `POST /api/v1/reports/aggregate`.
 * Mirrors the backend `AggregateReportResponse` DTO.
 *
 * `kind` is a discriminator: the backend may add other report kinds later.
 * For M6 it's always `'AGGREGATE'`.
 *
 * There is no `warnings` field on the success path — the backend returns
 * warnings only on the error path, inside `REPORT_BLOCKED`.
 */
export interface AggregateReportResponse {
  kind: 'AGGREGATE';
  dateRange: DateRange;
  groupKeys: GroupKey[];
  referenceCurrency: string;
  lines: AggregateLineDto[];
  warnings?: ReportWarningDto[];
}
