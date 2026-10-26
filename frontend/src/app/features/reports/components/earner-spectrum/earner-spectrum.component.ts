import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EarnerRankingDto } from '../../models';

@Component({
  selector: 'app-earner-spectrum',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './earner-spectrum.component.html',
  styleUrl: './earner-spectrum.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EarnerSpectrumComponent {
  readonly tops = input.required<EarnerRankingDto[]>();
  readonly bottoms = input.required<EarnerRankingDto[]>();
  readonly topTotalAnnual = input.required<number>();
  readonly bottomTotalAnnual = input.required<number>();
  readonly referenceCurrency = input.required<string>();
  readonly frequency = input.required<'MONTHLY' | 'BI_WEEKLY'>();

  readonly topRange = computed(() => this.computeRange(this.tops()));
  readonly bottomRange = computed(() => this.computeRange(this.bottoms()));

  annual(item: EarnerRankingDto): number {
    return item.monthlyReferenceAmount * 12;
  }

  formatAnnual(v: number): string {
    return v.toLocaleString('en-US', {
      style: 'currency', currency: this.referenceCurrency(),
      minimumFractionDigits: 0, maximumFractionDigits: 0,
    });
  }

  formatMonthly(v: number): string {
    return v.toLocaleString('en-US', {
      style: 'currency', currency: this.referenceCurrency(),
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    });
  }

  formatPercentile(p: number | null): string {
    if (p == null) return '—';
    const whole = Math.floor(p / 100);
    const frac  = String(p % 100).padStart(2, '0');
    return `P${whole}.${frac}`;
  }

  cadenceLabel(freq: 'MONTHLY' | 'BI_WEEKLY'): string {
    return freq === 'MONTHLY' ? '/ mo' : '/ bi-wk';
  }

  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1F1E6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }

  private computeRange(items: EarnerRankingDto[]): { min: number; max: number } {
    if (items.length === 0) return { min: 0, max: 0 };
    const values = items.map(i => this.annual(i));
    return { min: Math.min(...values), max: Math.max(...values) };
  }
}
