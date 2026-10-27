import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';

import { EmployeeAssignmentDto } from '../../models';
import {
  EMPLOYMENT_TYPE_LABELS,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
} from '../assignment-editor-form/assignment-form.model';

@Component({
  selector: 'app-assignment-pill',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
  templateUrl: './assignment-pill.component.html',
  styleUrl: './assignment-pill.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentPillComponent {
  readonly assignment = input.required<EmployeeAssignmentDto>();
  readonly endDateControl = input.required<FormControl<string | null>>();
  readonly endRequired = input<boolean>(false);
  readonly newAssignmentActive = input<boolean>(false);
  readonly disabled = input<boolean>(false);

  readonly toggleNewAssignment = output<void>();

  readonly employmentTypeLabel = computed(
    () => EMPLOYMENT_TYPE_LABELS[this.assignment().employmentType],
  );

  readonly frequencyLabel = computed(
    () => PAYROLL_PERIOD_FREQUENCY_LABELS[this.assignment().payrollFrequency],
  );

  readonly countryFlag = computed(() =>
    flagEmoji(this.assignment().country.code),
  );

  onToggle(): void {
    if (this.disabled()) return;
    this.toggleNewAssignment.emit();
  }
}

function flagEmoji(code: string | null | undefined): string {
  if (!code || code.length !== 2) return '';
  const base = 0x1f1e6;
  return String.fromCodePoint(
    base + (code.charCodeAt(0) - 65),
    base + (code.charCodeAt(1) - 65),
  );
}
