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
import { EarningsRankingReportFormComponent } from '../../components/earnings-ranking-report-form/earnings-ranking-report-form.component';
import { BlockerBannerComponent } from '../../components/blocker-banner/blocker-banner.component';
import { WarningBannerComponent } from '../../components/warning-banner/warning-banner.component';

type PageState = 'idle' | 'loading' | 'success' | 'error';

@Component({
  selector: 'app-earnings-ranking-report-page',
  standalone: true,
  imports: [
    EarningsRankingReportFormComponent,
    PositionContextComponent,
    EarnerSpectrumComponent,
    BlockerBannerComponent,
    WarningBannerComponent,
  ],
  templateUrl: './earnings-ranking-report.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EarningsRankingReportPage {
  private readonly reports = inject(ReportsService);

  readonly state = signal<PageState>('idle');
  readonly report = signal<EarningRankingReportResponse | null>(null);
  readonly error = signal<ReportsError | null>(null);

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

  onSubmit(request: EarningRankingReportRequest): void {
    this.state.set('loading');
    this.error.set(null);

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
    this.report.set(null);
    this.error.set(null);
    this.state.set('idle');
  }

  dismissBanner(): void {
    this.error.set(null);
    if (this.state() === 'error') this.state.set('idle');
  }
}
