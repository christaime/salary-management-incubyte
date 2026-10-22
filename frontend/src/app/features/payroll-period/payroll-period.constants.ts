import { PayrollPeriodDto } from './models';

/**
 * Stub payroll periods used while `GET /api/v1/payroll-periods` is not yet
 * wired into the frontend. Names, dates, and frequencies are representative.
 *
 * TODO(backend): delete once the endpoint is live and `PayrollPeriodsService`
 * flips to `useStub = false`.
 */
export const STUB_PAYROLL_PERIODS: ReadonlyArray<PayrollPeriodDto> =
  buildStubPeriods();

function buildStubPeriods(): PayrollPeriodDto[] {
  const periods: PayrollPeriodDto[] = [];
  const months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  // Monthly periods Jan 2025 → Aug 2026
  for (let year = 2025; year <= 2026; year++) {
    const lastMonth = year === 2026 ? 7 : 11; // stop at Aug 2026
    for (let m = 0; m <= lastMonth; m++) {
      const start = new Date(year, m, 1);
      const end = new Date(year, m + 1, 0); // last day of month
      periods.push({
        id: `${year}-${String(m + 1).padStart(2, '0')}-M`,
        name: `${months[m]} ${year}`,
        startDate: toIso(start),
        endDate: toIso(end),
        frequency: 'MONTHLY',
        closed: true,
      });
    }
  }

  // September 2026 — split in two bi-weekly periods for demo purposes
  periods.push({
    id: '2026-09-W1W2',
    name: 'September 2026 · W1–W2',
    startDate: '2026-09-01',
    endDate: '2026-09-14',
    frequency: 'BI_WEEKLY',
    closed: false,
  });
  periods.push({
    id: '2026-09-W3W4',
    name: 'September 2026 · W3–W4',
    startDate: '2026-09-15',
    endDate: '2026-09-30',
    frequency: 'BI_WEEKLY',
    closed: false,
  });

  return periods;
}

function toIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
