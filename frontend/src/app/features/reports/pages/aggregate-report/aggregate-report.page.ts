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
} from '../../models';
import { ValidationError } from '../../models/error-response.model';
import {
  AggregateReportFormComponent,
  AggregateReportFormValue,
} from '../../components/aggregate-report-form/aggregate-report-form.component';
import { AggregateReportResultsComponent } from '../../components/aggregate-report-results/aggregate-report-results.component';
import { ReportKindToggleComponent } from '../../components/report-kind-toggle/report-kind-toggle.component';

type PageState = 'idle' | 'submitting' | 'success' | 'error';

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

  /** Per-tab cache of last successful responses. */
  private readonly cache = signal<
    Partial<Record<ReportKind, AggregateReportResponse>>
  >({});

  /** Currently displayed response (from cache or fresh). */
  readonly reportResponse = signal<AggregateReportResponse | null>(null);
  readonly reportError = signal<ReportsError | null>(null);

  /** Last form value used for the most recent generate. */
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

  /**
   * True once a successful generate has happened at least once. Drives the
   * "toggle auto-regenerates" behaviour.
   */
  readonly hasEverGenerated = computed(
    () => Object.keys(this.cache()).length > 0,
  );

  // --- Actions ------------------------------------------------------------

  /** Form submitted (Generate clicked). */
  onFormSubmit(value: AggregateReportFormValue): void {
    this.lastFormValue = value;
    this.fetch(this.selectedKind(), value);
  }

  /** Toggle clicked. Uses cache if available, else triggers a fetch. */
  onKindChange(kind: ReportKind): void {
    if (kind === this.selectedKind() && this.reportResponse()) return;
    this.selectedKind.set(kind);

    // Cache hit?
    const cached = this.cache()[kind];
    if (cached) {
      this.reportResponse.set(cached);
      this.reportError.set(null);
      this.state.set('success');
      return;
    }

    // Cache miss. Only auto-generate if a form value is available.
    if (this.lastFormValue) {
      this.fetch(kind, this.lastFormValue);
    }
  }

  /** Reset: clear form, cache, state. */
  onFormReset(): void {
    this.cache.set({});
    this.reportResponse.set(null);
    this.reportError.set(null);
    this.state.set('idle');
    this.selectedKind.set('TOTAL');
    this.lastFormValue = null;
  }

  /** Dismiss error banner without touching the form. */
  dismissBanner(): void {
    this.reportError.set(null);
    if (this.state() === 'error') this.state.set('idle');
  }

  // --- Fetch --------------------------------------------------------------

  private fetch(
    kind: ReportKind,
    formValue: AggregateReportFormValue,
  ): void {
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
      // Breakdown is enabled so the donut panel can render on row click
      // without a second round-trip. One extra payload field is fine.
      includeBreakdown: true,
      includeStatistics: kind === 'TOTAL' ? true : false,
    };
  }

  // --- Error handling -----------------------------------------------------

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
        // The interceptor already toasted on 5xx / status 0.
        return;

      default:
        this.toast.error(err.message || 'Report request failed.');
    }
  }
}
