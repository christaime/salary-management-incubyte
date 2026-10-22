import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ToastService } from '../ui/toast/toast.service';
import { ApiError, FieldError } from './api-error';

/**
 * One entry of the API `ErrorResponse.errors` array.
 * The shape is `{ code, message, details }`, with the field name and any structured
 * context carried inside `details`.
 */
interface ApiErrorEntry {
  code: string;
  message: string;
  severity?: string | null;
  details?: Record<string, unknown> | null;
}

/**
 * API `ErrorResponse` shape.
 */
interface ApiErrorEnvelope {
  code?: string;
  message?: string;
  errors?: ApiErrorEntry[] | null;
  timestamp?: string;
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
  const body = err.error as ApiErrorEnvelope | string | null;
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

  const body = err.error as ApiErrorEnvelope | null;

  if (err.status === 0) {
    return {
      code: 'NETWORK_ERROR',
      message: 'Network error. Check your connection and try again.',
      fieldErrors: [],
      status: 0,
    };
  }

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

  const fieldErrors: FieldError[] = (body.errors ?? []).map(toFieldError);

  return {
    code: body.code,
    message: body.message ?? 'Request failed.',
    fieldErrors,
    status: err.status,
  };
}

/**
 * Converts one API `ApiErrorEntry` into a frontend `FieldError`.
 *
 * <p>The field name and any structured context are carried inside `details`. When
 * `details.field` is absent, the entry is not a field error (it is a form-level error,
 * a blocker, or a warning), and `field` is left empty so the caller can still surface
 * `message` at the top of the form.</p>
 */
function toFieldError(entry: ApiErrorEntry): FieldError {
  const details = entry.details ?? {};
  const field =
    typeof details['field'] === 'string' ? (details['field'] as string) : '';
  return {
    field,
    code: entry.code,
    message: entry.message,
    attributes: details,
  };
}
