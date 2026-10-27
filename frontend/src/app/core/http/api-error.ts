
export type ErrorSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface ApiError {
  code: string;
  message: string;
  severity: ErrorSeverity;
  errors?: ApiDetailError[];
}

export interface ApiDetailError {
  code: string;
  message: string;
  severity: ErrorSeverity;
  details?: Record<string, unknown>;
}
/** Type guard used by callers that receive an `unknown` error. */
export function isApiError(value: unknown): value is ApiError {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['code'] === 'string' &&
    typeof v['message'] === 'string' &&
    Array.isArray(v['fieldErrors']) &&
    typeof v['status'] === 'number'
  );
}
