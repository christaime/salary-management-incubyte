import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { apiUrl } from '@core/config/api-base-url';
import { AggregateReportRequest, AggregateReportResponse } from '../models';
import { EarningRankingReportRequest, EarningRankingReportResponse } from '../models';
import {
  ReportsError,
  ReportWarning,
  EmployeeWarning,
  MissingCurrencyRateWarning,
  MissingReferenceCurrencyWarning,
} from './reports-error';

const AGGREGATE_REPORT_URL = apiUrl('/api/v1/reports/aggregate');
const RANKING_REPORT_URL   = apiUrl('/api/v1/reports/rankings');

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  generateAggregateReport(request: AggregateReportRequest): Observable<AggregateReportResponse> {
    return this.http.post<AggregateReportResponse>(AGGREGATE_REPORT_URL, request)
      .pipe(catchError((err: unknown) => throwError(() => toReportsError(err))));
  }

  generateRankingReport(request: EarningRankingReportRequest ): Observable<EarningRankingReportResponse> {
      return this.http.post<EarningRankingReportResponse>(RANKING_REPORT_URL, request)
        .pipe(catchError((err: unknown) => throwError(() => toReportsError(err))));
    }
}

function toReportsError(err: any): ReportsError {
  const body = err.error ?? err;
  return {
    code: body.code ?? 'UNKNOWN_ERROR',
    message: body.message ?? 'An unexpected error occurred.',
    warnings: (body.errors ?? []).map(mapReportWarning),
  };
}

/**
 * Routes the mapping based on the warning code.
 */
export function mapReportWarning(raw: any): ReportWarning {
  switch (raw.code) {
    case 'MISSING_PAYROLL_SUMMARY':
    case 'UNVALIDATED_PAYROLL_SUMMARY':
      return mapToEmployeeWarning(raw);

    case 'MISSING_FX_RATE':
      return mapToMissingCurrencyRateWarning(raw);

    case 'MISSING_REFERENCE_CURRENCY':
      return mapToMissingReferenceCurrencyWarning(raw);

    case 'PERIOD_CLOSED':
    default:
      return {
        code: raw.code,
        message: raw.message,
        severity: raw.severity ?? 'BLOCKER',
      } as ReportWarning;
  }
}

export function mapToEmployeeWarning(raw: any): EmployeeWarning {
  const details = raw.details ?? {};
  const rawEmployees = details.employees ?? [];

  return {
    code: raw.code,
    message: raw.message,
    severity: raw.severity ?? 'BLOCKER',
    countConcerned: typeof details.count === 'number' ? details.count : rawEmployees.length,
    employees: rawEmployees.slice(0, 10).map((e: any) => ({
      id: e.id,
      matricule: e.matricule,
      name: e.name,
    })),
  };
}

export function mapToMissingCurrencyRateWarning(raw: any): MissingCurrencyRateWarning {
  return {
    code: raw.code,
    message: raw.message,
    severity: raw.severity ?? 'BLOCKER',
    currency: raw.details?.currency ?? 'UNKNOWN',
  };
}

export function mapToMissingReferenceCurrencyWarning(raw: any): MissingReferenceCurrencyWarning {
  return {
    code: raw.code,
    message: raw.message,
    severity: raw.severity ?? 'BLOCKER',
    currency: raw.details?.currency,
  };
}
