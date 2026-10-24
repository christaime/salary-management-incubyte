/**
 * Inclusive date range, serialized as ISO-8601 `YYYY-MM-DD` strings.
 * Mirrors the backend `DateRange` DTO.
 */
export interface DateRange {
  /** Inclusive start date, ISO `YYYY-MM-DD`. */
  startDate: string;
  /** Inclusive end date, ISO `YYYY-MM-DD`. */
  endDate: string;
}
