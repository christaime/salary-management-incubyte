import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PositionContextDto } from '../../models';

interface StripMarker {
  label: string;
  percent: number;
  amount: number;
  highlighted: boolean;
}

@Component({
  selector: 'app-position-context',
  standalone: true,
  templateUrl: './position-context.component.html',
  styleUrl: './position-context.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PositionContextComponent {
  readonly data = input.required<PositionContextDto>();
  readonly referenceCurrency = input.required<string>();

  readonly markers = computed<StripMarker[]>(() => {
    const d = this.data();
    return [
      { label: 'P0',   percent: 0,   amount: d.min, highlighted: false },
      { label: 'P10',  percent: 10,  amount: d.p10, highlighted: false },
      { label: 'P25',  percent: 25,  amount: d.p25, highlighted: false },
      { label: 'P50',  percent: 50,  amount: d.p50, highlighted: true  },
      { label: 'P75',  percent: 75,  amount: d.p75, highlighted: false },
      { label: 'P90',  percent: 90,  amount: d.p90, highlighted: false },
      { label: 'P100', percent: 100, amount: d.max, highlighted: false },
    ];
  });

  formatMoney(v: number): string {
    return v.toLocaleString('en-US', {
      style: 'currency',
      currency: this.referenceCurrency(),
      maximumFractionDigits: 0,
    });
  }
}
