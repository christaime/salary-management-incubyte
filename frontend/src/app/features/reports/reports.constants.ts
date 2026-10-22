import { LookupOption } from '@core/models/lookup-option';

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
