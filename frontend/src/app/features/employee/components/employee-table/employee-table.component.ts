import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { EmployeeWithInEffectAssignmentDto, EmploymentType, EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_LABELS, } from '../../models';

@Component({
  selector: 'app-employee-table',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './employee-table.component.html',
  styleUrl: './employee-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeTableComponent {
  readonly employees = input.required<EmployeeWithInEffectAssignmentDto[]>();
  readonly loading = input<boolean>(false);

  /** Emitted when the user clicks the Edit icon on a row. */
  readonly editClick = output<EmployeeWithInEffectAssignmentDto>();
  /** Emitted when the user clicks the Delete icon on a draft row. */
  readonly deleteClick = output<EmployeeWithInEffectAssignmentDto>();
  /** Emitted when the user clicks the More-actions icon. */
  readonly moreClick = output<EmployeeWithInEffectAssignmentDto>();

  // ---------- Presentational helpers ----------

  /** Turns an ISO country code (e.g. "FR") into a flag emoji (🇫🇷). */
  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1F1E6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }

  employmentTypeLabel(type: EmploymentType | null): string {
    if(type) return EMPLOYMENT_TYPE_LABELS[type];
    return '—';
  }

  /** True when the assignment has no end date (open-ended). */
  isOpenEnded(endDate: string | null): boolean {
    return endDate === null;
  }

  /** Formats ISO date as YYYY-MM-DD; falls back to "—". */
  formatDate(value: string | null): string {
    return value ?? '—';
  }
}
