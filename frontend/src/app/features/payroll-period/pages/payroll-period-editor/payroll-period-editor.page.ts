import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { merge, take } from 'rxjs';
import {
  FormArray,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { PayrollPeriodsService } from '../../services/payroll-periods.service';
import { PayrollPeriodError } from '../../services/payroll-period-error';
import {
  PayrollPeriodFormComponent,
  PayrollPeriodFormShape,
  PreviewRow,
  computeEndDate,
  monthlySpanValidator,
} from '../../components/payroll-period-form/payroll-period-form.component';
import {
  PayrollPeriodCreateRequest,
  PayrollPeriodDto,
  SubPayrollPeriodCreateResponse,
} from '@features/payroll-period/models';

type PageState = 'loading' | 'ready' | 'not-found' | 'error' | 'saving';

@Component({
  selector: 'app-payroll-period-editor-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    PayrollPeriodFormComponent,
  ],
  templateUrl: './payroll-period-editor.page.html',
  styleUrl: './payroll-period-editor.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayrollPeriodEditorPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly service = inject(PayrollPeriodsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly mode = signal<'new' | 'edit'>('new');
  readonly state = signal<PageState>('ready');
  readonly periodId = signal<string | null>(null);

  readonly formError = signal<string | null>(null);

  /** Set to `true` when only `name` may be edited. Drives the form's disabled state. */
  readonly nameOnly = signal(false);
  /** Message shown in the amber banner when `nameOnly` is on. */
  readonly nameOnlyNotice = signal<string | null>(null);
  /** Local dismissal flag; reset every time a new notice is set. */
  readonly nameOnlyNoticeDismissed = signal(false);

  readonly failedSummaries = signal<
    { label: string; startDate: string; endDate: string; message: string }[]
  >([]);
  readonly createdCount = signal(0);

  readonly previewRows = signal<PreviewRow[]>([]);
  readonly previewLoading = signal(false);
  readonly previewError = signal<string | null>(null);
  private lastGenerateSub: SubPayrollPeriodCreateResponse | null = null;

  private readonly userEditedEndDate = signal(false);
  private isProgrammaticEndDateWrite = false;

  readonly form = new FormGroup<PayrollPeriodFormShape>({
    name: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(100),
      ],
    }),
    frequency: new FormControl<'MONTHLY' | 'BI_WEEKLY'>('MONTHLY', {
      nonNullable: true,
    }),
    startDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    endDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, monthlySpanValidator],
    }),
    cascadeToBiWeekly: new FormControl(false, { nonNullable: true }),
    subPeriodNames: new FormArray<FormControl<string>>([]),
  });

  readonly canSubmit = computed(
    () => !this.formError()?.startsWith('This pay schedule is already settled'),
  );

  ngOnInit(): void {
    this.form.controls.endDate.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.isProgrammaticEndDateWrite) {
          this.userEditedEndDate.set(true);
        }
      });

    this.form.controls.startDate.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.userEditedEndDate.set(false);
        this.recomputeEndDate();
        this.form.controls.endDate.updateValueAndValidity({ emitEvent: false });
      });

    this.form.controls.frequency.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.userEditedEndDate.set(false);
        this.recomputeEndDate();
        this.form.controls.endDate.updateValueAndValidity({ emitEvent: false });
      });

    merge(
      this.form.controls.startDate.valueChanges,
      this.form.controls.endDate.valueChanges,
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.invalidateCascade());

    this.form.controls.cascadeToBiWeekly.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((checked) => {
        if (checked) {
          this.refreshCascadePreview();
        } else {
          this.clearPreview();
        }
      });

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const id = params.get('id');
        if (id) {
          this.mode.set('edit');
          this.periodId.set(id);
          this.loadPeriod(id);
        } else {
          this.mode.set('new');
          this.periodId.set(null);
          this.patchDefaultsForNew();
        }
      });
  }

  // ---------- Load ----------
  private loadPeriod(id: string): void {
    this.state.set('loading');
    this.formError.set(null);

    this.service.getById(id).pipe(take(1)).subscribe({
      next: (period) => {
        this.patchFormFromPeriod(period);
        this.state.set('ready');
      },
      error: (err: PayrollPeriodError) => {
        if (err.code === 'NOT_FOUND') {
          this.state.set('not-found');
        } else {
          this.formError.set(err.message || 'Failed to load pay schedule');
          this.state.set('error');
        }
      },
    });
  }

  private patchFormFromPeriod(period: PayrollPeriodDto): void {
    this.isProgrammaticEndDateWrite = true;
    try {
      this.form.patchValue({
        name: period.name,
        frequency: period.frequency,
        startDate: period.startDate,
        cascadeToBiWeekly: false,
      });
      this.userEditedEndDate.set(true);
      this.form.controls.endDate.setValue(period.endDate);
    } finally {
      this.isProgrammaticEndDateWrite = false;
    }
    this.recomputeEndDate();

    if (period.referenced === true) {
      this.nameOnly.set(true);
      this.showNameOnlyNotice();
    }
  }

  private patchDefaultsForNew(): void {
    this.form.reset({
      name: '',
      frequency: 'MONTHLY',
      startDate: '',
      endDate: '',
      cascadeToBiWeekly: false,
    });
    this.form.controls.subPeriodNames.clear({ emitEvent: false });

    const firstOfNextMonth = new Date();
    firstOfNextMonth.setMonth(firstOfNextMonth.getMonth() + 1, 1);
    this.form.patchValue({ startDate: toIso(firstOfNextMonth) });

    this.userEditedEndDate.set(false);
    this.recomputeEndDate();
    this.clearPreview();
    this.formError.set(null);
    this.nameOnly.set(false);
    this.nameOnlyNotice.set(null);
    this.nameOnlyNoticeDismissed.set(false);
    this.state.set('ready');
  }

  // ---------- Name-only notice ----------
  private showNameOnlyNotice(): void {
    this.nameOnlyNotice.set(
      'Payroll records exist for this pay schedule; only its name can be changed.',
    );
    this.nameOnlyNoticeDismissed.set(false);
  }

  onDismissNameOnlyNotice(): void {
    this.nameOnlyNoticeDismissed.set(true);
  }

  // ---------- Submit / cancel ----------
  onSubmit(): void {
    this.formError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    if (this.mode() === 'edit' && this.periodId()) {
      this.saveEdit();
      return;
    }

    if (this.form.controls.cascadeToBiWeekly.value) {
      this.saveCascadeBatch();
    } else {
      this.saveSingle();
    }
  }

  onCancel(): void {
    this.router.navigate(['/payroll-periods']);
  }

  // ---------- Saves ----------
  private saveSingle(): void {
    this.state.set('saving');
    this.service.create(this.toMonthlyRequest()).pipe(take(1)).subscribe({
      next: () => this.finishSuccess('Pay schedule created'),
      error: (err) => this.handleSubmitError(err),
    });
  }

  private saveEdit(): void {
    const id = this.periodId();
    if (!id) return;
    this.state.set('saving');
    this.service.update(id, this.toMonthlyRequest()).pipe(take(1)).subscribe({
      next: () => this.finishSuccess('Pay schedule updated'),
      error: (err) => this.handleSubmitError(err),
    });
  }

  private saveCascadeBatch(): void {
    if (!this.lastGenerateSub) {
      this.formError.set('Sub-schedules preview is not ready yet.');
      return;
    }
    const batch = [this.toMonthlyRequest(), ...this.buildCascadeBatch()];
    this.state.set('saving');
    this.service.createBatch(batch).pipe(take(1)).subscribe({
      next: (res) => this.handleBatchResult(res),
      error: (err) => this.handleSubmitError(err),
    });
  }

  private buildCascadeBatch(): PayrollPeriodCreateRequest[] {
    const sub = this.lastGenerateSub;
    if (!sub) return [];
    const names = this.form.controls.subPeriodNames.getRawValue();
    const newCount = sub.newPeriods.length;
    const newNames = names.slice(sub.exist.length, sub.exist.length + newCount);
    return sub.newPeriods.map((p, i) => ({
      name: (newNames[i] ?? p.name).trim(),
      startDate: p.startDate,
      endDate: p.endDate,
      frequency: 'BI_WEEKLY' as const,
    }));
  }

  private handleBatchResult(res: {
    succeeded: PayrollPeriodDto[];
    failed: {
      period: PayrollPeriodCreateRequest;
      errors: { code: string; message: string; details?: { field?: string } }[];
    }[];
  }): void {
    this.state.set('ready');
    if (res.failed.length === 0) {
      this.finishSuccess(
        res.succeeded.length > 1
          ? `${res.succeeded.length} pay schedules created`
          : 'Pay schedule created',
      );
      return;
    }
    this.createdCount.set(res.succeeded.length);
    this.failedSummaries.set(
      res.failed.map((f) => ({
        label: f.period.name,
        startDate: f.period.startDate,
        endDate: f.period.endDate,
        message: f.errors[0]?.message ?? 'Rejected',
      })),
    );
  }

  // ---------- Errors ----------
  private handleSubmitError(err: unknown): void {
    this.state.set('ready');
    const e = err as PayrollPeriodError;
    this.clearServerErrors();

    if (e.code === 'PERIOD_CLOSED') {
      this.formError.set(
        'This pay schedule is already settled and cannot be edited.',
      );
      return;
    }

    if (e.code === 'PERIOD_REFERENCED_NAME_ONLY') {
      this.nameOnly.set(true);
      this.form.controls.frequency.disable({ emitEvent: false });
      this.form.controls.startDate.disable({ emitEvent: false });
      this.form.controls.endDate.disable({ emitEvent: false });
      this.showNameOnlyNotice();
      return;
    }

    if (e.code === 'DATA_CONFLICT') {
      if (this.applyConflictErrors(e)) return;
    }

    this.formError.set(e.message || 'Save failed');
  }

  private applyConflictErrors(e: PayrollPeriodError): boolean {
    let applied = false;
    for (const item of e.errors ?? []) {
      if (item.code === 'PERIOD_NAME_CONFLICT') {
        this.form.controls.name.setErrors({ serverError: item.message });
        applied = true;
      }
      if (item.code === 'PERIOD_RANGE_CONFLICT') {
        this.form.controls.startDate.setErrors({ serverError: item.message });
        this.form.controls.endDate.setErrors({ serverError: item.message });
        applied = true;
      }
    }
    return applied;
  }

  private clearServerErrors(): void {
    const keys: (keyof PayrollPeriodFormShape)[] = [
      'name',
      'frequency',
      'startDate',
      'endDate',
      'cascadeToBiWeekly',
    ];
    keys.forEach((k) => {
      const c = this.form.controls[k];
      const errs = c.errors;
      if (errs && 'serverError' in errs) {
        const { serverError, ...rest } = errs;
        c.setErrors(Object.keys(rest).length ? rest : null);
      }
    });
  }

  private finishSuccess(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 3000 });
    this.router.navigate(['/payroll-periods']);
  }

  // ---------- Derived values + preview ----------
  private recomputeEndDate(): void {
    const start = this.form.controls.startDate.value;
    const freq = this.form.controls.frequency.value;
    if (!start) return;

    if (freq === 'BI_WEEKLY' || !this.userEditedEndDate()) {
      const suggested = computeEndDate(start, freq);
      if (this.form.controls.endDate.value !== suggested) {
        this.isProgrammaticEndDateWrite = true;
        this.form.controls.endDate.setValue(suggested, { emitEvent: false });
        this.isProgrammaticEndDateWrite = false;
      }
    }
  }

  private invalidateCascade(): void {
    if (this.form.controls.cascadeToBiWeekly.value) {
      this.form.controls.cascadeToBiWeekly.setValue(false);
    }
  }

  private clearPreview(): void {
    if (this.previewRows().length > 0) this.previewRows.set([]);
    if (this.previewError() !== null) this.previewError.set(null);
    if (this.previewLoading()) this.previewLoading.set(false);
    this.lastGenerateSub = null;
    if (this.form.controls.subPeriodNames.length > 0) {
      this.form.controls.subPeriodNames.clear({ emitEvent: false });
    }
  }

  private refreshCascadePreview(): void {
    const start = this.form.controls.startDate.value;
    const end = this.form.controls.endDate.value;

    this.previewLoading.set(true);
    this.previewError.set(null);

    this.service.generateSubPeriods(start, end).pipe(take(1)).subscribe({
      next: (sub) => {
        this.lastGenerateSub = sub;
        const rows: PreviewRow[] = [
          ...sub.exist.map<PreviewRow>((p) => ({
            suggestedName: p.name,
            kind: 'existing',
            startDate: p.startDate,
            endDate: p.endDate,
            editable: false,
          })),
          ...sub.newPeriods.map<PreviewRow>((p) => ({
            suggestedName: p.name,
            kind: 'new',
            startDate: p.startDate,
            endDate: p.endDate,
            editable: true,
          })),
        ];
        this.previewRows.set(rows);

        this.form.controls.subPeriodNames.clear();
        [...sub.exist, ...sub.newPeriods].forEach((p) => {
          this.form.controls.subPeriodNames.push(
            new FormControl<string>(p.name, { nonNullable: true }),
          );
        });

        this.previewLoading.set(false);
      },
      error: (err: PayrollPeriodError) => {
        this.lastGenerateSub = null;
        this.previewRows.set([]);
        this.form.controls.subPeriodNames.clear();
        this.previewError.set(err.message || 'Preview failed');
        this.previewLoading.set(false);
      },
    });
  }

  private toMonthlyRequest(): PayrollPeriodCreateRequest {
    const v = this.form.getRawValue();
    return {
      name: v.name.trim(),
      startDate: v.startDate,
      endDate: v.endDate,
      frequency: v.frequency,
    };
  }
}

function toIso(d: Date): string {
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}
