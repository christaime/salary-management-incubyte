import { CommonModule } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { EMPLOYMENT_TYPE_LABELS } from '../../models'
import { AggregateLineDto, AmountsDto } from '../../models';

interface DonutSlice {
  key: keyof Pick<AmountsDto, 'base' | 'variable' | 'allowances' | 'employerTaxes'>;
  label: string;
  value: number;
  percent: number;
  dasharray: string;
  dashoffset: number;
  color: string;
}

// Corrected labels to match the backend fields
const SLICE_DEFS = [
  { key: 'base', label: 'Base Salary', color: '#0f172a' },             // Slate-900
  { key: 'variable', label: 'Variable Pay', color: '#2563eb' },        // Blue-600
  { key: 'allowances', label: 'Allowances', color: '#60a5fa' },        // Blue-400
  { key: 'employerTaxes', label: 'Employer Taxes', color: '#cbd5e1' }, // Slate-300
] as const;

const CIRCUMFERENCE = 376.99; // 2 * PI * 60

@Component({
  selector: 'app-breakdown-donut',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './breakdown-donut.component.html',
  styleUrl: './breakdown-donut.component.scss',
})
export class BreakdownDonutComponent {
  readonly line = input<AggregateLineDto | null>(null);

  readonly hasBreakdown = computed(() => this.line()?.breakdown !== undefined);

  readonly title = computed(() => {
    const l = this.line();
    if (!l) return 'Composition';
    const employmentTypeLabel = l.employmentType? EMPLOYMENT_TYPE_LABELS[l.employmentType as keyof typeof EMPLOYMENT_TYPE_LABELS] : null;
    return l.country?.name ?? l.department?.name ?? l.role?.name ?? employmentTypeLabel ?? l.currency ?? 'Composition';
  });

  readonly total = computed(() => {
    const b = this.line()?.breakdown;
    if (!b) return 0;
    return b.base + b.variable + b.allowances + b.employerTaxes;
  });

  readonly currency = computed(() => this.line()?.breakdown?.currency ?? this.line()?.amount.currency ?? 'USD');

  readonly slices = computed<DonutSlice[]>(() => {
    const b = this.line()?.breakdown;
    if (!b) return [];
    const total = this.total() || 1;

    let cursor = 0;
    return SLICE_DEFS.map((def) => {
      const value = b[def.key];
      const percent = (value / total) * 100;
      const dash = (percent / 100) * CIRCUMFERENCE;
      const offset = -cursor;
      cursor += dash;

      return {
        key: def.key,
        label: def.label,
        value,
        percent,
        dasharray: `${dash} ${CIRCUMFERENCE - dash}`,
        dashoffset: offset,
        color: def.color,
      };
    }).filter((s) => s.value > 0);
  });

  formatAmount(value: number): string {
    return value.toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
  }
}
