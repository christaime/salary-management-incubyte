import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  input,
  signal,
  effect,
} from '@angular/core';

import {
  AggregateLineDto,
  AggregateReportResponse,
} from '../../models';
import { TieredBarListComponent } from '../tiered-bar-list/tiered-bar-list.component';
import { BreakdownDonutComponent } from '../breakdown-donut/breakdown-donut.component';
import { CompensationCadenceCardComponent } from '../compensation-cadence-card/compensation-cadence-card.component';

@Component({
  selector: 'app-aggregate-report-results',
  standalone: true,
  imports: [CommonModule, TieredBarListComponent, BreakdownDonutComponent, CompensationCadenceCardComponent],
  templateUrl: './aggregate-report-results.component.html',
  styleUrl: './aggregate-report-results.component.scss',
})
export class AggregateReportResultsComponent {
  readonly response = input.required<AggregateReportResponse>();

  /** Selected line for the donut. Reset when the response changes. */
  readonly selectedLine = signal<AggregateLineDto | null>(null);

  readonly isEmpty = computed(() => this.response().lines.length === 0);

  /** True when the report has zero group keys — a single-row "total" view. */
  readonly isTotal = computed(() => this.response().groupKeys.length === 0);

  readonly totalAmount = computed(() => {
    const r = this.response();
    if (r.lines.length === 0) return 0;
    return r.lines.reduce((sum, l) => sum + l.amount.amount, 0);
  });

  readonly currency = computed(() => this.response().referenceCurrency);

  readonly totalEmployees = computed(() =>
    this.response().lines.reduce((sum, l) => sum + l.employeeCount, 0),
  );

  statistics(line: AggregateLineDto){
    return line.statistics;
  }

  constructor() {
    // Reset selection whenever the response changes.
    effect(() => {
      this.response();
      this.selectedLine.set(null);
    });
  }

  onRowClick(line: AggregateLineDto): void {
    this.selectedLine.set(line);
  }

  formatAmount(value: number): string {
    return value.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  readonly hasWarnings = computed(() => this.response().warnings.length > 0);

  readonly selectedKey = computed<string | null>(() => {
    const line = this.selectedLine();
    if (!line) return null;
    return (
      line.country?.code ?? line.department?.code ?? line.role?.code ?? line.employmentType ?? line.currency ?? null
    );
  });

  readonly reportTitle = computed(() => {
    const keys = this.response().groupKeys;
    if (keys.length === 0) return 'Total Payroll';

    switch (keys[0]) {
      case 'COUNTRY': return 'Payroll Cost per Country';
      case 'DEPARTMENT': return 'Payroll Cost per Department';
      case 'ROLE': return 'Payroll Cost per Role';
      case 'EMPLOYMENT_TYPE': return 'Payroll Cost per Employment Type';
      case 'CURRENCY': return 'Payroll Cost per Currency';
      default: return 'Payroll Breakdown';
    }
  });

  readonly reportSubtitle = computed(() => {
    const keys = this.response().groupKeys;
    if (keys.length === 0) return 'Total employer expenditure for the selected period';

    switch (keys[0]) {
      case 'COUNTRY': return 'Comparative employer expenditure ranked by country budget share';
      case 'DEPARTMENT': return 'Comparative employer expenditure ranked by department budget share';
      case 'ROLE': return 'Comparative employer expenditure ranked by role budget share';
      case 'EMPLOYMENT_TYPE': return 'Comparative employer expenditure ranked by employment type';
      default: return 'Comparative employer expenditure';
    }
  });
}
