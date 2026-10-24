import { LookupOption } from '@core/models/lookup-option';

/**
 * Cadence at which a payroll period repeats.
 * Mirrors the backend `PayrollPeriodFrequency` enum.
 */
export type PayrollPeriodFrequency = 'MONTHLY' | 'BI_WEEKLY';

export const PAYROLL_PERIOD_FREQUENCIES: ReadonlyArray<PayrollPeriodFrequency> = [
  'MONTHLY',
  'BI_WEEKLY',
];

export const PAYROLL_PERIOD_FREQUENCY_LABELS: Readonly<
  Record<PayrollPeriodFrequency, string>
> = {
  MONTHLY: 'Monthly',
  BI_WEEKLY: 'Bi-weekly',
};

/** Convenience: frequency labels as lookup options (used by filters). */
export const PAYROLL_PERIOD_FREQUENCY_OPTIONS: ReadonlyArray<LookupOption> = [
  { id: 'MONTHLY', label: 'Monthly' },
  { id: 'BI_WEEKLY', label: 'Bi-weekly' },
];
