import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { BaseReportWarning } from '../../services/reports-error';

@Component({
  selector: 'app-blocker-banner',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './blocker-banner.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BlockerBannerComponent {
  readonly blockers = input.required<BaseReportWarning[]>();
  readonly dismiss = output<void>();
}
