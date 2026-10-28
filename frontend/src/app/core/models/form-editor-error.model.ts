/**
 * A field error the editor can attach to a specific FormControl.
 * `field` matches the control path (e.g. "matricule", "placement.startDate").
 */
export interface FieldError {
  field: string;
  code: string;        // "Conflict", "Email", etc.
  message: string;     // human-readable
}

/**
 * Simple error envelope for the employee editor.
 * One `errors` array, one `message`, no nesting.
 */
export interface FormEditorError {
  code: string;                 // "VALIDATION_FAILED", "NETWORK_OR_SERVER_ERROR"
  message: string;
  errors: FieldError[];
}
