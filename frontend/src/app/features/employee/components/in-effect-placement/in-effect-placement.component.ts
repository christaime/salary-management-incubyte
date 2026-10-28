import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

import {
  AssignmentEditorFormComponent,
} from '../assignment-editor-form/assignment-editor-form.component';
import {
  AssignmentEditorForm,
  createAssignmentEditorForm,
} from '../assignment-editor-form/assignment-form.model';
import {
  AssignmentPillComponent,
} from '../assignment-pill/assignment-pill.component';
import { EmployeeAssignmentDto } from '../../models';

export interface InEffectPlacementForm {
  current: FormGroup<AssignmentEditorForm>;
  next: FormGroup<AssignmentEditorForm>;
}

/** Cross-field: `current.endDate` is required while `next.enabled`. */
const endRequiredWhenAddingValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const form = group as FormGroup<InEffectPlacementForm>;
  const next = form.controls.next;
  if (!next || !next.enabled) return null;
  const end = form.controls.current.get('endDate')?.value as string | null;
  return end ? null : { endRequiredWhenAdding: true };
};

@Component({
  selector: 'app-in-effect-placement',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    AssignmentEditorFormComponent,
    AssignmentPillComponent,
  ],
  templateUrl: './in-effect-placement.component.html',
  styleUrl: './in-effect-placement.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InEffectPlacementComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly formGroup = input.required<FormGroup<InEffectPlacementForm>>();

  /** The current in-effect assignment as loaded from the API (may be null in create mode). */
  readonly currentAssignment = input<EmployeeAssignmentDto | null>(null);

  /** True when the current assignment already has payroll and can only be closed. */
  readonly hasPayroll = input<boolean>(false);

  /** True while the parent is loading or saving (disables toggles and buttons). */
  readonly disabled = input<boolean>(false);

  /** True when the toggle is on (the new assignment form is visible). */
  readonly newAssignmentActive = signal<boolean>(false);

  /** True when the pill should be shown (edit + hasPayroll). */
  readonly showPill = computed(() => this.hasPayroll() && this.currentAssignment() !== null);

  /** True when the current assignment form is editable (create or !hasPayroll). */
  readonly showCurrentEditor = computed(() => !this.showPill());

  /** True when the "new assignment" form is rendered. */
  readonly showNextEditor = computed(() => this.newAssignmentActive());

  /** Exposed to the template so the pill can bind directly. */
  readonly currentEndControl = computed<FormControl<string | null>>(() =>
    this.formGroup().controls.current.controls.endDate,
  );

  ngOnInit(): void {
    const group = this.formGroup();

    // Apply the cross-field "endDate required while adding" rule on the outer group.
    group.addValidators(endRequiredWhenAddingValidator);
    group.updateValueAndValidity();

    // Disable `next` initially — the toggle is the source of truth.
    const next = group.controls.next;
    if (!this.newAssignmentActive()) {
      next.disable({ emitEvent: false });
    }

    // Re-evaluate the group validator when the toggle changes.
    // The parent drives the toggle via `onToggle`, so we only need to
    // update validity when `next`'s enabled state flips.
    next.statusChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => group.updateValueAndValidity({ emitEvent: false }));
  }

  onToggleNewAssignment(): void {
    if (this.disabled()) return;

    const group = this.formGroup();
    const next = group.controls.next;
    const active = this.newAssignmentActive();

    if (active) {
      // Turning OFF: keep values, disable the form.
      next.disable({ emitEvent: false });
      this.newAssignmentActive.set(false);
    } else {
      // Turning ON: enable the form, and if it was previously populated, keep values.
      next.enable({ emitEvent: false });
      this.newAssignmentActive.set(true);
    }

    // Re-run the cross-field validator on `current` (endDate required when adding).
    group.updateValueAndValidity();
  }
}

/** Factory for the in-effect placement form. */
export function createInEffectPlacementForm(): FormGroup<InEffectPlacementForm> {
  return new FormGroup<InEffectPlacementForm>({
    current: createAssignmentEditorForm(),
    next: createAssignmentEditorForm(),
  });
}
