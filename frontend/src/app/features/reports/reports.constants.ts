/**
 * Static stubs for the reports feature.
 *
 * TODO(M6.3+): replace with data loaded from the API once the following
 * endpoints exist:
 *   - GET /api/v1/countries
 *   - GET /api/v1/departments
 *   - GET /api/v1/roles
 *
 * NOTE: ORIGIN_COUNTRY is intentionally not exposed as a group key in the
 * current milestone. The backend DTO may eventually support it; the frontend
 * will not offer it until that support is real.
 */

export interface LookupOption {
  id: string;
  label: string;
}

/** ISO 3166-1 alpha-2 codes. Enough for the M6 form to work end-to-end. */
export const STUB_COUNTRIES: ReadonlyArray<LookupOption> = [
  { id: 'CM', label: 'Cameroon' },
  { id: 'FR', label: 'France' },
  { id: 'DE', label: 'Germany' },
  { id: 'GB', label: 'United Kingdom' },
  { id: 'US', label: 'United States' },
];

export const STUB_DEPARTMENTS: ReadonlyArray<LookupOption> = [
  { id: 'engineering', label: 'Engineering' },
  { id: 'sales', label: 'Sales' },
  { id: 'hr', label: 'Human Resources' },
  { id: 'finance', label: 'Finance' },
];

export const STUB_ROLES: ReadonlyArray<LookupOption> = [
  { id: 'engineer', label: 'Engineer' },
  { id: 'manager', label: 'Manager' },
  { id: 'director', label: 'Director' },
  { id: 'analyst', label: 'Analyst' },
];
