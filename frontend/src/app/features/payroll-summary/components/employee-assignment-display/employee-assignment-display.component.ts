import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

import {
  EmployeeWithInEffectAssignmentDto,
  EMPLOYMENT_TYPE_LABELS,
} from '@features/employee/models';
import { PAYROLL_PERIOD_FREQUENCY_LABELS } from '@features/payroll-period/models';

/**
 * Read-only display of a selected employee's in-effect assignment.
 * Renders:
 *   1. The selected employee bar (unless `onlyAssignment` is true).
 *   2. A 7-pill summary of the assignment.
 *   3. A "no in-effect assignment" error state when applicable.
 */
@Component({
  selector: 'app-employee-assignment-display',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './employee-assignment-display.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeAssignmentDisplayComponent {
  readonly employee = input.required<EmployeeWithInEffectAssignmentDto>();
  readonly onlyAssignment = input<boolean>(false);

  readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;
  readonly frequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  countryFlag = (code: string): string => {
    if (!code || code.length !== 2) return '';
    const upper = code.toUpperCase();
    const base = 0x1f1e6;
    return String.fromCodePoint(
      base + upper.charCodeAt(0) - 65,
      base + upper.charCodeAt(1) - 65,
    );
  };
}
