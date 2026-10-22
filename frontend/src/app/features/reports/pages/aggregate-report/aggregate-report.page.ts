import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { take } from 'rxjs';

import { ToastService } from '../../../../core/ui/toast/toast.service';
import { ReportsService } from '../../services/reports.service';
import { ReportsError } from '../../services/reports-error';
import {
  AggregateReportRequest,
  AggregateReportResponse,
  groupKeysFor,
  ReportKind,
  ReportWarningDto,
} from '../../models';
import { ValidationError } from '../../models/error-response.model';
import {
  AggregateReportFormComponent,
  AggregateReportFormValue,
} from '../../components/aggregate-report-form/aggregate-report-form.component';
import { AggregateReportResultsComponent } from '../../components/aggregate-report-results/aggregate-report-results.component';
import { ReportKindToggleComponent } from '../../components/report-kind-toggle/report-kind-toggle.component';

type PageState = 'idle' | 'submitting' | 'success' | 'error';

/** Typed employee entry inside a blocker's details. */
interface BlockerEmployee {
  id: string;
  matricule: string;
  name: string;
}

@Component({
  selector: 'app-aggregate-report-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AggregateReportFormComponent,
    AggregateReportResultsComponent,
    ReportKindToggleComponent,
  ],
  templateUrl: './aggregate-report.page.html',
  styleUrl: './aggregate-report.page.scss',
})
export class AggregateReportPageComponent {
  private readonly reports = inject(ReportsService);
  private readonly toast = inject(ToastService);

  // --- State --------------------------------------------------------------

  readonly state = signal<PageState>('idle');
  readonly selectedKind = signal<ReportKind>('TOTAL');

  private readonly cache = signal<
    Partial<Record<ReportKind, AggregateReportResponse>>
  >({});

  readonly reportResponse = signal<AggregateReportResponse | null>(null);
  readonly reportError = signal<ReportsError | null>(null);

  private lastFormValue: AggregateReportFormValue | null = null;

  // --- Derived ------------------------------------------------------------

  readonly isLoading = computed(() => this.state() === 'submitting');

  readonly blockers = computed(() => this.reportError()?.blockers ?? []);

  readonly fieldErrors = computed<readonly ValidationError[]>(
    () => this.reportError()?.fieldErrors ?? [],
  );

  readonly showBlockerBanner = computed(() => this.blockers().length > 0);

  readonly showFieldErrors = computed(
    () =>
      this.state() === 'error' &&
      this.reportError()?.code === 'VALIDATION_FAILED' &&
      this.fieldErrors().length > 0,
  );

  readonly showResults = computed(
    () => this.state() === 'success' && this.reportResponse() !== null,
  );

  readonly hasEverGenerated = computed(
    () => Object.keys(this.cache()).length > 0,
  );

  // --- Blocker detail accessors ------------------------------------------

  /**
   * Typed `count` from a blocker's details, or `0` when absent.
   *
   * <p>{@code details} is an open map from the backend, so the narrowing lives here
   * rather than in the template.</p>
   */
  blockerCount(blocker: ReportWarningDto): number {
    const count = blocker.details?.['count'];
    return typeof count === 'number' ? count : 0;
  }

  /**
   * Typed employee list from a blocker's details, or an empty array when absent
   * or malformed.
   */
  blockerEmployees(blocker: ReportWarningDto): BlockerEmployee[] {
    const employees = blocker.details?.['employees'];
    if (!Array.isArray(employees)) return [];
    return employees.filter(
      (e): e is BlockerEmployee =>
        typeof e === 'object' &&
        e !== null &&
        typeof (e as { id?: unknown }).id === 'string' &&
        typeof (e as { matricule?: unknown }).matricule === 'string' &&
        typeof (e as { name?: unknown }).name === 'string',
    );
  }

  // --- Actions ------------------------------------------------------------

  onFormSubmit(value: AggregateReportFormValue): void {
    this.lastFormValue = value;
    this.fetch(this.selectedKind(), value);
  }

  onKindChange(kind: ReportKind): void {
    if (kind === this.selectedKind() && this.reportResponse()) return;
    this.selectedKind.set(kind);

    const cached = this.cache()[kind];
    if (cached) {
      this.reportResponse.set(cached);
      this.reportError.set(null);
      this.state.set('success');
      return;
    }

    if (this.lastFormValue) {
      this.fetch(kind, this.lastFormValue);
    }
  }

  onFormReset(): void {
    this.cache.set({});
    this.reportResponse.set(null);
    this.reportError.set(null);
    this.state.set('idle');
    this.selectedKind.set('TOTAL');
    this.lastFormValue = null;
  }

  dismissBanner(): void {
    this.reportError.set(null);
    if (this.state() === 'error') this.state.set('idle');
  }

  // --- Fetch --------------------------------------------------------------

  private fetch(kind: ReportKind, formValue: AggregateReportFormValue): void {
    this.state.set('submitting');
    this.reportError.set(null);

    const request = this.buildRequest(kind, formValue);

    this.reports
      .generateAggregateReport(request)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.reportResponse.set(response);
          this.cache.update((m) => ({ ...m, [kind]: response }));
          this.reportError.set(null);
          this.state.set('success');
        },
        error: (err: ReportsError) => {
          this.reportError.set(err);
          this.reportResponse.set(null);
          this.state.set('error');
          this.handleError(err);
        },
      });
  }

  private buildRequest(
    kind: ReportKind,
    formValue: AggregateReportFormValue,
  ): AggregateReportRequest {
    const start = formValue.startPeriod;
    const end = formValue.endPeriod ?? start;

    return {
      dateRange: {
        startDate: start.startDate,
        endDate: end.endDate,
      },
      groupKeys: groupKeysFor(kind),
      anyCountryIds: formValue.filters.countryIds,
      anyDepartmentIds: formValue.filters.departmentIds,
      anyRoleIds: formValue.filters.roleIds,
      anyEmploymentTypes: formValue.filters.employmentTypes,
      includeBreakdown: true,
      includeStatistics: kind === 'TOTAL',
    };
  }

  private handleError(err: ReportsError): void {
    switch (err.code) {
      case 'REPORT_BLOCKED':
        this.toast.error(
          'Report could not be generated. Fix the data and try again.',
        );
        return;

      case 'VALIDATION_FAILED':
        return;

      case 'INTERNAL_ERROR':
      case 'SERVER_ERROR':
      case 'NETWORK_ERROR':
        return;

      default:
        this.toast.error(err.message || 'Report request failed.');
    }
  }
}
