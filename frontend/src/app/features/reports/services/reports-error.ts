export type ReportWarningCode =
  | 'PERIOD_CLOSED'
  | 'MISSING_PAYROLL_SUMMARY'
  | 'UNVALIDATED_PAYROLL_SUMMARY'
  | 'MISSING_FX_RATE'
  | 'MISSING_REFERENCE_CURRENCY'
  | 'NOT_MONTHLY_PERIOD';

export interface ConcernedEmployee {
  id: string;
  matricule: string;
  name: string;
}

// --- Base Type ---
export interface BaseReportWarning {
  code: ReportWarningCode;
  message: string;
  severity: 'WARNING' | 'BLOCKER';
}

// --- Specific Types ---
export interface EmployeeWarning extends BaseReportWarning {
  code: 'MISSING_PAYROLL_SUMMARY' | 'UNVALIDATED_PAYROLL_SUMMARY';
  countConcerned: number;
  employees: ConcernedEmployee[];
}

export interface MissingCurrencyRateWarning extends BaseReportWarning {
  code: 'MISSING_FX_RATE';
  currency: string;
}

export interface MissingReferenceCurrencyWarning extends BaseReportWarning {
  code: 'MISSING_REFERENCE_CURRENCY';
  currency?: string; // Might be missing or present depending on how the backend sends it
}

export interface PeriodClosedWarning extends BaseReportWarning {
  code: 'PERIOD_CLOSED';
}

export interface NotMonthlyPeriodWarning extends BaseReportWarning {
  code: 'NOT_MONTHLY_PERIOD';
}

// --- The Union ---
export type ReportWarning =
  | EmployeeWarning
  | MissingCurrencyRateWarning
  | MissingReferenceCurrencyWarning
  | PeriodClosedWarning
  | NotMonthlyPeriodWarning;

export interface ReportsError {
  code: string;
  message: string;
  warnings: ReportWarning[];
}
