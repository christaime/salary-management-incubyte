import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { take } from 'rxjs';

import { ToastService } from '../../../../core/ui/toast/toast.service';
import { ReportsService, mapReportWarning } from '../../services/reports.service';
import { ReportsError, BaseReportWarning } from '../../services/reports-error';
import {
  AggregateReportRequest,
  AggregateReportResponse,
  groupKeysFor,
  ReportKind,
} from '../../models';
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

  readonly state = signal<PageState>('idle');
  readonly selectedKind = signal<ReportKind>('TOTAL');

  // The Map indicating which reports have been fetched for the current criteria
  private readonly reportCache = signal<
    Partial<Record<ReportKind, AggregateReportResponse>>
  >({});

  readonly reportResponse = signal<AggregateReportResponse | null>(null);
  readonly reportError = signal<ReportsError | null>(null);

  // Stores the criteria used for the current generation cycle
  private lastSubmittedFormValue: AggregateReportFormValue | null = null;

  readonly isLoading = computed(() => this.state() === 'submitting');

  readonly blockers = computed<BaseReportWarning[]>(
    () => (this.reportError()?.warnings ?? []) as BaseReportWarning[]
  );

  readonly warnings = computed<BaseReportWarning[]>(() => {
    const res = this.reportResponse();
    if (!res?.warnings) return [];
    return res.warnings.map(mapReportWarning) as BaseReportWarning[];
  });

  readonly showBlockerBanner = computed(() => this.blockers().length > 0);
  readonly showWarningsBanner = computed(() => this.warnings().length > 0);

  readonly showResults = computed(
    () => this.state() === 'success' && this.reportResponse() !== null,
  );

  /**
   * Called when the form emits formChange.
   * This happens when the period changes OR when the user clicks Apply in the drawer.
   * We clear the map, the current results, and the last submitted value.
   */
  onFormChange(): void {
    this.reportResponse.set(null);
    this.reportError.set(null);
    this.reportCache.set({});
    this.state.set('idle');
    this.selectedKind.set('TOTAL');
    this.lastSubmittedFormValue = null;
  }

  /**
   * Called when the user clicks the Generate button.
   */
  onFormSubmit(value: AggregateReportFormValue): void {
    this.lastSubmittedFormValue = value;
    this.reportCache.set({});
    this.fetch(this.selectedKind(), value);
  }

  /**
   * Called when the user clicks a toggle button.
   */
  onKindChange(kind: ReportKind): void {
    if (kind === this.selectedKind() && this.reportResponse()) return;
    this.selectedKind.set(kind);

    // Check the Map: Do we already have this report for the current criteria?
    const cached = this.reportCache()[kind];
    if (cached) {
      this.reportResponse.set(cached);
      this.reportError.set(null);
      this.state.set('success');
      return;
    }

    // If not cached, but we have a valid form value, fetch it
    if (this.lastSubmittedFormValue) {
      this.fetch(kind, this.lastSubmittedFormValue);
    }
  }

  onFormReset(): void {
    this.onFormChange();
  }

  dismissBanner(): void {
    this.reportError.set(null);
    if (this.state() === 'error') this.state.set('idle');
  }

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
          this.reportCache.update((m) => ({ ...m, [kind]: response }));
          this.reportError.set(null);
          this.state.set('success');
        },
        error: (err: ReportsError) => {
          this.reportError.set(err);
          this.reportResponse.set(null);
          this.state.set('error');
          if (err.code === 'REPORT_BLOCKED') {
            this.toast.error('Report could not be generated. Fix the data and try again.');
          } else {
            this.toast.error(err.message || 'Report request failed.');
          }
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
}
