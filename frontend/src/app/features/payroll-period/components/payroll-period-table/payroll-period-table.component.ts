import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  PAYROLL_PERIOD_FREQUENCY_LABELS,
  PayrollPeriodDto,
  PayrollPeriodFrequency,
} from '@features/payroll-period/models';

@Component({
  selector: 'app-payroll-period-table',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './payroll-period-table.component.html',
  styleUrl: './payroll-period-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodTableComponent {
  readonly rows = input.required<PayrollPeriodDto[]>();
  readonly loading = input<boolean>(false);

  readonly editClick = output<PayrollPeriodDto>();
  readonly deleteClick = output<PayrollPeriodDto>();
  readonly settleClick = output<PayrollPeriodDto>();

  frequencyLabel(value: PayrollPeriodFrequency): string {
    return PAYROLL_PERIOD_FREQUENCY_LABELS[value];
  }
}
