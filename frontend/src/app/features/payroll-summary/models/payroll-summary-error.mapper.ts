import { FieldError, FormEditorError } from '@core/models/form-editor-error.model';

/**
 * Field to highlight for each payroll-summary specific backend code.
 * `null` means "show as a form-level banner, not attached to a control".
 *
 * Keep this in sync with the backend error contract.
 */
const CODE_TO_FIELD: Record<string, string | null> = {
  SUMMARY_ALREADY_EXISTS: 'employeeId',
  PERIOD_CLOSED: 'periodId',
  SUMMARY_NOT_DRAFT: null,
  ASSIGNMENT_NOT_IN_EFFECT: 'assignmentId',
  ASSIGNMENT_NOT_FOR_EMPLOYEE: 'assignmentId',
  ASSIGNMENT_DOES_NOT_COVER_PERIOD: 'assignmentId',
  UNEXPECTED_ERROR: null,
};

/**
 * The subset of form-field names this mapper can produce. `null` means
 * "no specific field — render as a general banner."
 */
export type PayrollSummaryErrorField =
  | 'employeeId'
  | 'periodId'
  | 'assignmentId'
  | null;

/**
 * Map a backend error code to the form field it should attach to, or
 * `null` if it is a form-level error. Used by `PayrollSummaryEditorPage`
 * to build a `{ field: message }` record for the form's `serverErrors`
 * input.
 */
export function mapPayrollSummaryErrorCode(
  code: string | null | undefined,
): PayrollSummaryErrorField {
  if (!code) return null;
  const field = CODE_TO_FIELD[code];
  return (field as PayrollSummaryErrorField) ?? null;
}

/**
 * Normalizes an ApiError (already mapped by the HTTP interceptor) into
 * the flat FormEditorError the editor UI consumes.
 *
 * The API returns errors under `api.errors[]` (ApiDetailError shape).
 * Each entry's `details.field` (when present) wins over the code-based
 * default, so the backend can override the field placement if needed.
 */
export function toPayrollEditorError(
  api: {
    code: string;
    message: string;
    errors?: Array<{
      code?: string;
      message?: string;
      details?: { field?: string };
    }>;
  },
): FormEditorError {
  const rawErrors = Array.isArray(api.errors) ? api.errors : [];

  const errors: FieldError[] = rawErrors
    .map((e) => {
      const backendField = e.details?.field;
      const mappedField = backendField ?? CODE_TO_FIELD[e.code ?? ''] ?? '';
      return {
        field: String(mappedField ?? ''),
        code: String(e.code ?? 'UNKNOWN'),
        message: String(e.message ?? 'Unknown error'),
      };
    })
    .filter((e) => e.field !== ''); // only field-level errors

  return {
    code: api.code ?? 'UNKNOWN',
    message: api.message ?? 'Unexpected error',
    errors,
  };
}
