import { CommonModule } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { EMPLOYMENT_TYPE_LABELS } from '../../models'
import { AggregateLineDto, AggregateReportResponse, GroupKey } from '../../models';

interface BarRow {
  key: string;
  label: string;
  code: string | null;
  employeeCount: number;
  amount: number;
  currency: string;
  barPct: number;
  percentage: number;
  line: AggregateLineDto;
  isTier1: boolean;
  isWarning: boolean;
}

@Component({
  selector: 'app-tiered-bar-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tiered-bar-list.component.html',
  styleUrl: './tiered-bar-list.component.scss',
})
export class TieredBarListComponent {
  readonly response = input.required<AggregateReportResponse>();
  readonly rowClick = output<AggregateLineDto>();
  readonly selectedKey = input<string | null>(null);

  readonly rows = computed<BarRow[]>(() => {
    const r = this.response();
    const groupKey: GroupKey | undefined = r.groupKeys[0];
    const maxAmount = Math.max(1, ...r.lines.map((l) => Math.abs(l.amount.amount)));
    const totalAmount = r.lines.reduce((sum, l) => sum + l.amount.amount, 0);

    return r.lines
      .map((line) => {
        const { label, code } = describeLine(line, groupKey);
        const amount = line.amount.amount;
        const percentage = totalAmount !== 0 ? (amount / totalAmount) * 100 : 0;
        return {
          key: code ?? label,
          label,
          code,
          employeeCount: line.employeeCount,
          amount,
          currency: line.amount.currency,
          barPct: (Math.abs(amount) / maxAmount) * 100,
          percentage,
          line,
          isTier1: false, // Set below
          isWarning: false // Set below
        };
      })
      .sort((a, b) => b.amount - a.amount)
      .map((row, index) => ({
        ...row,
        // Stitch spec: first item gets the primary gradient, others get secondary
        isTier1: index === 0,
        // Mocking the "France 41.5% (Highest)" warning for visual demo
        isWarning: false
      }));
  });

  readonly currency = computed(() => this.response().referenceCurrency);

  formatAmount(value: number): string {
    return value.toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
  }

  onRowClick(row: BarRow): void {
    this.rowClick.emit(row.line);
  }
}

function describeLine(
  line: AggregateLineDto,
  groupKey: GroupKey | undefined,
): { label: string; code: string | null } {
  switch (groupKey) {
    case 'COUNTRY': return { label: line.country?.name ?? '—', code: line.country?.code ?? null };
    case 'DEPARTMENT': return { label: line.department?.name ?? '—', code: line.department?.code ?? null };
    case 'ROLE': return { label: line.role?.name ?? '—', code: line.role?.code ?? null };
    case 'EMPLOYMENT_TYPE': return { label: line.employmentType? EMPLOYMENT_TYPE_LABELS[line.employmentType as keyof typeof EMPLOYMENT_TYPE_LABELS] : '—', code: null };
    case 'CURRENCY': return { label: line.currency ?? '—', code: line.currency ?? null };
    case undefined: return { label: 'Total', code: null };
  }
}
