/**
 * Dimensions by which the aggregate report can group lines.
 *
 * NOTE: `ORIGIN_COUNTRY` is intentionally NOT included in this milestone.
 * The backend DTO may eventually support it; the frontend will not offer it
 * until that support is real. When it lands, add it here and to the form's
 * group-keys selector.
 */
export type GroupKey =
  | 'COUNTRY'
  | 'DEPARTMENT'
  | 'ROLE'
  | 'EMPLOYMENT_TYPE'
  | 'CURRENCY';

/** All currently supported group keys, in display order. */
export const GROUP_KEYS: ReadonlyArray<GroupKey> = [
  'COUNTRY',
  'DEPARTMENT',
  'ROLE',
  'EMPLOYMENT_TYPE',
  'CURRENCY',
];
