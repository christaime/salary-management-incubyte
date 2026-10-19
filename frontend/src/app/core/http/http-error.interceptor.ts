import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ToastService } from '../ui/toast/toast.service';
import { ApiError, FieldError } from './api-error';

/**
 * Backend `ErrorResponse` shape (see backend `ErrorResponse.java`).
 * Kept local to the interceptor — the reports feature has its own typed copy
 * in `features/reports/models/error-response.model.ts` when it needs it.
 */
interface BackendErrorResponse {
  code?: string;
  message?: string;
  errors?: Array<{
    field: string;
    code: string;
    message: string;
    attributes?: Record<string, unknown>;
  }> | null;
}

/**
 * Maps any `HttpErrorResponse` into an `ApiError` and rethrows it.
 * Also surfaces a toast for errors that no caller is expected to handle
 * inline (network failures, 5xx, unparsable bodies).
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: unknown) => {
      const apiError = toApiError(err);

      // 4xx with a parseable body should be handled by the caller
      // (e.g. inline form errors, REPORT_BLOCKED banner).
      // 0 / 5xx and unparsable errors get a toast as a safety net.
      const shouldToast =
        apiError.status === 0 ||
        apiError.status >= 500 ||
        !isParsedBody(err);

      if (shouldToast) {
        toast.error(apiError.message);
      }

      return throwError(() => apiError);
    }),
  );
};

function isParsedBody(err: unknown): boolean {
  if (!(err instanceof HttpErrorResponse)) return false;
  const body = err.error as BackendErrorResponse | string | null;
  return typeof body === 'object' && body !== null && typeof body.code === 'string';
}

function toApiError(err: unknown): ApiError {
  if (!(err instanceof HttpErrorResponse)) {
    return {
      code: 'CLIENT_ERROR',
      message: 'An unexpected client error occurred.',
      fieldErrors: [],
      status: 0,
    };
  }

  const body = err.error as BackendErrorResponse | null;

  // Network failure or CORS error — no body.
  if (err.status === 0) {
    return {
      code: 'NETWORK_ERROR',
      message: 'Network error. Check your connection and try again.',
      fieldErrors: [],
      status: 0,
    };
  }

  // Body wasn't the expected shape (HTML error page, empty 500, etc.).
  if (!body || typeof body.code !== 'string') {
    return {
      code: err.status >= 500 ? 'SERVER_ERROR' : 'HTTP_ERROR',
      message:
        err.status >= 500
          ? 'The server encountered an error. Please try again later.'
          : err.message || 'Request failed.',
      fieldErrors: [],
      status: err.status,
    };
  }

  const fieldErrors: FieldError[] = (body.errors ?? []).map((e) => ({
    field: e.field,
    code: e.code,
    message: e.message,
    attributes: e.attributes ?? {},
  }));

  return {
    code: body.code,
    message: body.message ?? 'Request failed.',
    fieldErrors,
    status: err.status,
  };
}
