import { GroupKey } from './group-key.model';

/**
 * The five report views available in the UI. Each maps to a `groupKeys`
 * value in the request:
 *
 *  - `TOTAL`            → `[]`
 *  - `COUNTRY`          → `['COUNTRY']`
 *  - `DEPARTMENT`       → `['DEPARTMENT']`
 *  - `ROLE`             → `['ROLE']`
 *  - `EMPLOYMENT_TYPE`  → `['EMPLOYMENT_TYPE']`
 */
export type ReportKind =
  | 'TOTAL'
  | 'COUNTRY'
  | 'DEPARTMENT'
  | 'ROLE'
  | 'EMPLOYMENT_TYPE';

export const REPORT_KINDS: ReadonlyArray<ReportKind> = [
  'TOTAL',
  'COUNTRY',
  'DEPARTMENT',
  'ROLE',
  'EMPLOYMENT_TYPE',
];

export const REPORT_KIND_LABELS: Readonly<Record<ReportKind, string>> = {
  TOTAL: 'Total payroll cost',
  COUNTRY: 'Payroll cost per countries',
  DEPARTMENT: 'Payroll cost per departments',
  ROLE: 'Payroll cost per role',
  EMPLOYMENT_TYPE: 'Payroll cost per employment type',
};

export function groupKeysFor(kind: ReportKind): GroupKey[] {
  switch (kind) {
    case 'TOTAL':
      return [];
    case 'COUNTRY':
      return ['COUNTRY'];
    case 'DEPARTMENT':
      return ['DEPARTMENT'];
    case 'ROLE':
      return ['ROLE'];
    case 'EMPLOYMENT_TYPE':
      return ['EMPLOYMENT_TYPE'];
  }
}
