import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { take } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { PayrollSummaryService } from '../../services/payroll-summary.service';
import { PayrollSummaryDetailDto } from '../../models/payroll-summary.model';
import {
  PayrollSummaryCreateRequest,
  PayrollSummaryUpdateRequest,
} from '../../models/payroll-summary-request.model';
import {
  PayrollSummaryEditorFormComponent,
  EditorMode,
  EditorSavePayload,
} from '../../components/payroll-summary-editor-form/payroll-summary-editor-form.component';
import { mapPayrollSummaryErrorCode } from '../../models/payroll-summary-error.mapper';

type PageState = 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-payroll-summary-editor',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollSummaryEditorFormComponent,
  ],
  templateUrl: './payroll-summary-editor.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollSummaryEditorPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(PayrollSummaryService);

  readonly detailRoute = '/payroll-summaries';

  readonly state = signal<PageState>('loading');
  readonly error = signal<string | null>(null);
  readonly summary = signal<PayrollSummaryDetailDto | null>(null);
  readonly saving = signal(false);
  readonly serverErrors = signal<Record<string, string>>({});

  /**
   * True when the editor was opened via a non-existent list row. The
   * summary was pre-loaded from the service signal; on save we POST to
   * create, not PUT to update.
   */
  readonly isNonExistent = signal(false);

  readonly mode = computed<EditorMode>(() =>
    this.route.snapshot.paramMap.get('id') ? 'update' : 'create',
  );

  ngOnInit(): void {
    this.loadSummary();
  }

  private loadSummary(): void {
    if (this.mode() === 'create') {
      const preset = this.service.selectedNonExistent();
      if (preset) {
        this.summary.set(preset);
        this.isNonExistent.set(true);
        this.service.clearSelectedNonExistent();
      }
      this.state.set('loaded');
      return;
    }

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.state.set('error');
      this.error.set('Missing summary id');
      return;
    }

    this.state.set('loading');
    this.error.set(null);

    this.service
      .getById(id)
      .pipe(take(1))
      .subscribe({
        next: (dto) => {
          this.summary.set(dto);
          this.state.set('loaded');
        },
        error: (err) => {
          this.error.set(err?.message ?? 'Failed to load payroll summary');
          this.state.set('error');
        },
      });
  }

  onSave(payload: EditorSavePayload): void {
    this.saving.set(true);
    this.serverErrors.set({});

    // Non-existent payroll → this is a CREATE, not an update. Same for
    // empty create-mode. Only real /:id edits hit `update`.
    const request$ =
      this.mode() === 'update' && !this.isNonExistent()
        ? this.service.update(
            this.summary()!.id,
            payload as PayrollSummaryUpdateRequest,
          )
        : this.service.create(payload as PayrollSummaryCreateRequest);

    request$.pipe(take(1)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.router.navigate([this.detailRoute, saved.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.serverErrors.set(this.toServerErrors(err));
      },
    });
  }

  onCancel(): void {
    const s = this.summary();
    if (s) this.router.navigate([this.detailRoute, s.id]);
    else this.router.navigate([this.detailRoute]);
  }

  onBackToList(): void {
    this.router.navigate([this.detailRoute]);
  }

  private toServerErrors(err: any): Record<string, string> {
    const code = err?.error?.code as string | undefined;
    const field = mapPayrollSummaryErrorCode(code);
    const message = err?.error?.message ?? err?.message ?? 'Save failed';
    if (field) return { [field]: message };
    return { __general: message };
  }
}
