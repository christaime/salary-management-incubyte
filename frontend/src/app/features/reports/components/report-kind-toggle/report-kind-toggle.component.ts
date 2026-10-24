import { CommonModule } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

import {
  REPORT_KINDS,
  REPORT_KIND_LABELS,
  ReportKind,
} from '../../models';

@Component({
  selector: 'app-report-kind-toggle',
  standalone: true,
  imports: [CommonModule, MatButtonToggleModule],
  templateUrl: './report-kind-toggle.component.html',
  styleUrl: './report-kind-toggle.component.scss',
})
export class ReportKindToggleComponent {
  readonly selected = input.required<ReportKind>();
  readonly disabled = input<boolean>(false);
  readonly kindChange = output<ReportKind>();

  readonly kinds = REPORT_KINDS;
  readonly labels = REPORT_KIND_LABELS;

  onSelectionChange(kind: ReportKind): void {
    this.kindChange.emit(kind);
  }
}
