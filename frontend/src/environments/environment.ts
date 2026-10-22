/**
 * Development environment. Backend runs separately on localhost:8085.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8085',
} as const;
