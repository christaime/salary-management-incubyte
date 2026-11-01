import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PayrollSummaryDto } from '../../models/payroll-summary.model';
import { PayrollSummaryStatusBadgeComponent } from '../payroll-summary-status-badge/payroll-summary-status-badge.component';
import {
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  EmploymentType,
} from '@features/employee/models';

@Component({
  selector: 'app-payroll-summary-table',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollSummaryStatusBadgeComponent,
  ],
  templateUrl: './payroll-summary-table.component.html',
  styleUrl: './payroll-summary-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryTableComponent {
  readonly rows = input.required<PayrollSummaryDto[]>();
  readonly loading = input<boolean>(false);
  readonly nonExistent = input<boolean>(false);

  /** Emitted when the user clicks the Edit icon on a DRAFT row. */
  readonly editClick = output<PayrollSummaryDto>();
  /** Emitted when the user clicks the Delete icon on a DRAFT row. */
  readonly deleteClick = output<PayrollSummaryDto>();
  /** Emitted when the user clicks the Validate icon on a DRAFT row. */
  readonly validateClick = output<PayrollSummaryDto>();

  /** Emits when the row body is clicked (for navigating to detail). */
  readonly rowClick = output<PayrollSummaryDto>();

  /** Turns an ISO country code (e.g. "FR") into a flag emoji (🇫🇷). */
  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1f1e6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }

  employmentTypeLabel(type: EmploymentType): string {
    return EMPLOYMENT_TYPE_LABELS[type];
  }

  /** Formats money with the row's own currency code. */
  formatMoney(amount: number, currency: string): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  /** Compact date range for the period column. */
  formatPeriodRange(startDate: string, endDate: string): string {
    return `${startDate} → ${endDate}`;
  }

  rowKey(row: PayrollSummaryDto): string {
    return row.id || `${row.employee.id}@${row.period.id}`;
  }
}
