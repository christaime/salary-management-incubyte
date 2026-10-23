import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error'; // Adjust path to your models

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let apiError: ApiError;

      // Extract structured ApiError from backend envelope if present
      if (error.error && typeof error.error === 'object' && 'code' in error.error) {
        apiError = error.error as ApiError;
      } else {
        // Fallback for network failures or unhandled server crashes (500s, 0, etc.)
        apiError = {
          code: 'NETWORK_OR_SERVER_ERROR',
          message: error.message || 'An unexpected server error occurred.',
          severity: 'ERROR',
        };
      }
      // Handle non-blocking warnings vs blocking errors
      if (apiError.severity === 'WARNING') {
        // Handle non-blocking warnings (e.g., trigger a snackbar/toast warning)
        console.warn(`[API Warning] ${apiError.code}: ${apiError.message}`, apiError.details);
      } else {
        // Handle standard blocking errors
        console.error(`[API Error] ${apiError.code}: ${apiError.message}`, apiError.details);
      }

      // Pass the normalized apiError downstream to components/services
      return throwError(() => apiError);
    })
  );
};
