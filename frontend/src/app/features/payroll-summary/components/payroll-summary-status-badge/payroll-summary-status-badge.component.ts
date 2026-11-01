import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PayrollSummaryStatus } from '../../models/payroll-summary.model';

@Component({
  selector: 'app-payroll-summary-status-badge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payroll-summary-status-badge.component.html',
  styleUrl: './payroll-summary-status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryStatusBadgeComponent {
  readonly status = input.required<PayrollSummaryStatus>();

  readonly label = computed(() =>
    this.status() === 'DRAFT' ? 'Draft' : 'Validated',
  );

  readonly testIdSuffix = computed(() =>
    this.status() === 'DRAFT' ? 'draft' : 'validated',
  );
}
