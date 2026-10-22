import { CommonModule } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { StatisticsDto } from '../../models/statistics.model';

@Component({
  selector: 'app-compensation-cadence-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './compensation-cadence-card.component.html',
  styleUrl: './compensation-cadence-card.component.scss'
})
export class CompensationCadenceCardComponent {
  readonly data = input.required<StatisticsDto>();

  readonly totalEmployees = computed(() => {
    const d = this.data();
    return d.monthly.employeeCount + d.biWeekly.employeeCount;
  });

  readonly monthlyRatio = computed(() => {
    const total = this.totalEmployees();
    return total === 0 ? 0 : (this.data().monthly.employeeCount / total) * 100;
  });

  readonly biWeeklyRatio = computed(() => {
    const total = this.totalEmployees();
    return total === 0 ? 0 : (this.data().biWeekly.employeeCount / total) * 100;
  });

  // --- Monthly Calculations ---
  readonly monthlySpread = computed(() => {
    const m = this.data().monthly;
    return m.max.amount - m.min.amount;
  });

  readonly monthlyAvgPosition = computed(() => {
    const m = this.data().monthly;
    const spread = m.max.amount - m.min.amount;
    if (spread <= 0) return 50;
    return Math.max(0, Math.min(100, ((m.average.amount - m.min.amount) / spread) * 100));
  });

  readonly monthlyAnnualized = computed(() => {
    // Assuming standard annualization (Mean periodic pay * 12 or adjusted by periods)
    return this.data().monthly.average.amount * 12;
  });

  // --- Bi-Weekly Calculations ---
  readonly biWeeklySpread = computed(() => {
    const b = this.data().biWeekly;
    return b.max.amount - b.min.amount;
  });

  readonly biWeeklyAvgPosition = computed(() => {
    const b = this.data().biWeekly;
    const spread = b.max.amount - b.min.amount;
    if (spread <= 0) return 50;
    return Math.max(0, Math.min(100, ((b.average.amount - b.min.amount) / spread) * 100));
  });

  // Normalized monthly equivalent: (Bi-Weekly Average * 26 annual periods) / 12 months
  readonly biWeeklyMonthlyEquivalent = computed(() => {
    return (this.data().biWeekly.average.amount * 26) / 12;
  });

  readonly biWeeklyAnnualized = computed(() => {
    return this.data().biWeekly.average.amount * 26;
  });
}
