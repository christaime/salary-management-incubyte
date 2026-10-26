import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { BaseReportWarning } from '../../services/reports-error';

@Component({
  selector: 'app-warning-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './warning-banner.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WarningBannerComponent {
  readonly warnings = input.required<BaseReportWarning[]>();
}
