import { AmountDto } from './amount.model';
import { AmountsDto } from './amounts.model';
import { StatisticsDto } from './statistics.model';

/**
 * One row of the aggregate report.
 * Mirrors the backend `AggregateLineDto`.
 *
 * Which group-key properties are present depends on the request's `groupKeys`:
 * a line grouped by `COUNTRY` will have `country` set and the other group
 * fields undefined. `breakdown` is present only when `includeBreakdown` was
 * requested; `statistics` only when `includeStatistics` was requested.
 */
export interface AggregateLineDto {
  /** The line's monetary total in `referenceCurrency`. Always present. */
  amount: AmountDto;
  employeeCount: number;

  /** Present only when `COUNTRY` was requested as a group key. */
  country?: string;
  /** Present only when `DEPARTMENT` was requested as a group key. */
  department?: string;
  /** Present only when `ROLE` was requested as a group key. */
  role?: string;
  /** Present only when `EMPLOYMENT_TYPE` was requested as a group key. */
  employmentType?: string;
  /** Present only when `CURRENCY` was requested as a group key. */
  currency?: string;

  /** Present only when `includeBreakdown = true`. */
  breakdown?: AmountsDto;

  /** Present only when `includeStatistics = true`. */
  statistics?: StatisticsDto;
}
