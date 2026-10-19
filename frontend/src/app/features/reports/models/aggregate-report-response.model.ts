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
 */
export interface AggregateReportResponse {
  kind: 'AGGREGATE';
  dateRange: DateRange;
  /** Group keys the response is grouped by, in display order. */
  groupKeys: GroupKey[];
  /** ISO 4217 code that `amount` is expressed in on every line. */
  referenceCurrency: string;
  lines: AggregateLineDto[];
  /** Non-fatal issues; may be empty. */
  warnings: ReportWarningDto[];
}
