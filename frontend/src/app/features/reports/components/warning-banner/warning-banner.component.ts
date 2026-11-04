import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ReportWarning } from '@features/reports/services/reports-error';

@Component({
  selector: 'app-warning-banner',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './warning-banner.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WarningBannerComponent {
  readonly warnings = input.required<ReportWarning[]>();

  /** Local, component-owned "dismissed" flag. Resets when the input changes. */
  private readonly dismissed = signal(false);

  readonly visible = computed(
    () => this.warnings().length > 0 && !this.dismissed(),
  );

  constructor() {
    // Whenever the parent hands us a *new* warnings array (a fresh
    // check-close response, a retry, a different period), clear the
    // dismissal so the banner shows again. Same reference → nothing to do.
    effect(() => {
      // Reading `warnings()` here is what registers the dependency.
      this.warnings();
      this.dismissed.set(false);
    });
  }

  onDismiss(): void {
    this.dismissed.set(true);
  }
}
