/**
 * A monetary amount in a specific currency.
 * Mirrors the backend `AmountDto`.
 *
 * The backend scales `amount` to the currency's fraction digits (typically 2)
 * before serializing, so the frontend only formats — it never re-rounds.
 */
export interface AmountDto {
  amount: number;
  /** ISO 4217 currency code, e.g. `EUR`, `USD`. */
  currency: string;
}
