/**
 * Employment types supported by the report API.
 * Mirrors the backend `EmploymentType` enum.
 */
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACTOR';

/** All employment types, in display order. */
export const EMPLOYMENT_TYPES: ReadonlyArray<EmploymentType> = [
  'FULL_TIME',
  'PART_TIME',
  'CONTRACTOR',
];

/** Human-readable labels for the employment types. */
export const EMPLOYMENT_TYPE_LABELS: Readonly<Record<EmploymentType, string>> = {
  FULL_TIME: 'Full time',
  PART_TIME: 'Part time',
  CONTRACTOR: 'Contractor',
};
