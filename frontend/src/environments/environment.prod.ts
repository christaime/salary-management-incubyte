/**
 * Production environment. The frontend is served by the backend at the same
 * origin, so API calls use relative URLs (`/api/v1/...`) — no host prefix.
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
} as const;
