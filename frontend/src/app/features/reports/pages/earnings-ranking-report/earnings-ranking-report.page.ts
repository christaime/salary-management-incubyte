import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { take } from 'rxjs';

import { ReportsService } from '../../services/reports.service';
import { ReportsError, BaseReportWarning } from '../../services/reports-error';
import {
  EarningRankingReportRequest,
  EarningRankingReportResponse,
} from '../../models';
import { PositionContextComponent } from '../../components/position-context/position-context.component';
import { EarnerSpectrumComponent } from '../../components/earner-spectrum/earner-spectrum.component';
import { EarningsRankingReportFormComponent, EarningsRankingFormValue } from '../../components/earnings-ranking-report-form/earnings-ranking-report-form.component';
import { BlockerBannerComponent } from '../../components/blocker-banner/blocker-banner.component';
import { WarningBannerComponent } from '../../components/warning-banner/warning-banner.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PageState } from '@core/models';
import { EMPLOYMENT_TYPE_LABELS } from '@features/employee/models';

@Component({
  selector: 'app-earnings-ranking-report-page',
  standalone: true,
  imports: [
    EarningsRankingReportFormComponent,
    PositionContextComponent,
    EarnerSpectrumComponent,
    BlockerBannerComponent,
    WarningBannerComponent,
    MatProgressSpinnerModule
  ],
  templateUrl: './earnings-ranking-report.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EarningsRankingReportPage {
  private readonly reports = inject(ReportsService);

  readonly state = signal<PageState>('idle');
  readonly report = signal<EarningRankingReportResponse | null>(null);
  readonly error = signal<ReportsError | null>(null);

  private lastSubmittedFormValue: EarningsRankingFormValue | null = null;
  readonly currentFormValue = signal<EarningsRankingFormValue | null>(null);

  readonly isLoading = computed(() => this.state() === 'loading');

  readonly blockers = computed<BaseReportWarning[]>(
    () => (this.error()?.warnings ?? []) as BaseReportWarning[],
  );

  readonly warnings = computed<BaseReportWarning[]>(
    () => (this.report()?.warnings ?? []) as BaseReportWarning[],
  );

  readonly showBlockerBanner = computed(() => this.blockers().length > 0);
  readonly showWarningsBanner = computed(() => this.warnings().length > 0);

  readonly topTotalAnnual = computed(() =>
    (this.report()?.tops ?? []).reduce(
      (s, t) => s + t.monthlyReferenceAmount * 12,
      0,
    ),
  );

  readonly bottomTotalAnnual = computed(() =>
    (this.report()?.bottoms ?? []).reduce(
      (s, t) => s + t.monthlyReferenceAmount * 12,
      0,
    ),
  );

  readonly hasData = computed(() => {
    const r = this.report();
    if (!r) return false;
    return r.tops.length > 0 || r.bottoms.length > 0;
  });

  readonly reportDescription = computed(() => {
    const formVal = this.currentFormValue() ?? this.lastSubmittedFormValue;
    if (!formVal || !formVal.period) {
      return 'Pick a payroll period and a ranking view.';
    }

    const periodName = formVal.period.name;
    let groupPart = '';

    if (formVal.groupKey === 'COUNTRY') {
      groupPart = ' in country';
      if (formVal.country) {
        groupPart += ` ${formVal.country.name}`;
      }
    } else if (formVal.groupKey === 'DEPARTMENT') {
      groupPart = ' in';
      if (formVal.department) {
        groupPart += ` ${formVal.department.name} department`;
      }
    } else if (formVal.groupKey === 'ROLE') {
      groupPart = ' and';
      if (formVal.role) {
        groupPart += ` ${formVal.role.name} role`;
      }
    } else if (formVal.groupKey === 'EMPLOYMENT_TYPE') {
      groupPart = '';
      if (formVal.employmentType) {
        const typeLabel = EMPLOYMENT_TYPE_LABELS[formVal.employmentType] ?? formVal.employmentType;
        groupPart += `and ${typeLabel} employment type`;
      }
    } else {
      groupPart = ' globally';
    }

    return `Top and bottom 5 earners for period '${periodName}'${groupPart}.`;
  });

  onFormChange(value: EarningsRankingFormValue | null = null): void {
    this.currentFormValue.set(value);
    this.report.set(null);
    this.error.set(null);
    this.state.set('idle');
    this.lastSubmittedFormValue = null;
  }

  onSubmitForm(value: EarningsRankingFormValue): void {
    this.currentFormValue.set(value);
    this.lastSubmittedFormValue = value;
    this.state.set('loading');
    this.error.set(null);

    const request: EarningRankingReportRequest = {
      periodId: value.period!.id,
      groupKey: value.groupKey,
      countryId: value.groupKey === 'COUNTRY' ? (value.country?.id ?? null) : null,
      departmentId: value.groupKey === 'DEPARTMENT' ? (value.department?.id ?? null) : null,
      roleId: value.groupKey === 'ROLE' ? (value.role?.id ?? null) : null,
      employmentType: value.groupKey === 'EMPLOYMENT_TYPE' ? value.employmentType : null,
    };

    this.reports
      .generateRankingReport(request)
      .pipe(take(1))
      .subscribe({
        next: (r) => {
          this.report.set(r);
          this.state.set('success');
        },
        error: (err: ReportsError) => {
          this.error.set(err);
          this.report.set(null);
          this.state.set('error');
        },
      });
  }

  onCancel(): void {
    this.onFormChange(null);
  }

  dismissBanner(): void {
    this.error.set(null);
    if (this.state() === 'error') this.state.set('idle');
  }
}
