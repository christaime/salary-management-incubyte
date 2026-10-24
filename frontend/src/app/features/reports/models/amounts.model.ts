/**
 * Salary breakdown per aggregate line.
 * Mirrors the backend `AmountsDto`.
 *
 * All component amounts share a single `currency`. `total` is the sum of
 * `base + variable + allowances + employerTaxes`.
 */
export interface AmountsDto {
  base: number;
  variable: number;
  allowances: number;
  employerTaxes: number;
  total: number;
  /** ISO 4217 currency code, e.g. `EUR`, `USD`. */
  currency: string;
}
