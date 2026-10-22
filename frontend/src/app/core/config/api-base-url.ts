import { environment } from '@env/environment';

/**
 * Prefixes a path with the API base URL from the active environment.
 *
 *  - dev:  `http://localhost:8085` + `/api/v1/foo` → `http://localhost:8085/api/v1/foo`
 *  - prod: `''`                     + `/api/v1/foo` → `/api/v1/foo`
 *
 * Always call this with a path starting with `/api/` (or similar); do not
 * include a trailing slash on the base URL — the helper inserts it.
 */
export function apiUrl(path: string): string {
  const base = environment.apiBaseUrl;
  if (!base) return path;
  return base + path;
}
