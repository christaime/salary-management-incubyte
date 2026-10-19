import { AmountDto } from './amount.model';

/**
 * Min / max / average per aggregate line.
 * Mirrors the backend `StatisticsDto`.
 *
 * All three fields are nullable because a line can exist without a value
 * for one of the statistics (e.g. min and max are equal, or the sample size
 * is small). The results table must render a placeholder (`—`) for null.
 */
export interface StatisticsDto {
  min: AmountDto | null;
  max: AmountDto | null;
  average: AmountDto | null;
}
