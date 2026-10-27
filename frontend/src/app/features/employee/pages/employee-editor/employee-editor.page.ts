import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { take } from 'rxjs';

import { EmployeeService } from '../../services/employee.service';
import {
  EmployeeIdentityComponent,
  EmployeeIdentityForm,
  createEmployeeIdentityForm,
} from '../../components/employee-identity/employee-identity.component';
import {
  InEffectPlacementComponent,
  InEffectPlacementForm,
  createInEffectPlacementForm,
} from '../../components/in-effect-placement/in-effect-placement.component';

import {
  EmployeeAssignmentDto,
  EmployeeAssignmentSaveRequest,
  EmployeeAssignmentToCloseRequest,
  EmployeeSaveRequest,
  EmployeeWithInEffectAssignmentDto,
} from '../../models';
import { FormEditorError, FieldError } from '@core/models/form-editor-error.model';

type PageState = 'idle' | 'loading' | 'saving' | 'success' | 'error';

interface EditorForm {
  identity: FormGroup<EmployeeIdentityForm>;
  inEffectPlacement: FormGroup<InEffectPlacementForm>;
}

@Component({
  selector: 'app-employee-editor-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    EmployeeIdentityComponent,
    InEffectPlacementComponent,
  ],
  templateUrl: './employee-editor.page.html',
  styleUrl: './employee-editor.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeEditorPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly employeeService = inject(EmployeeService);

  readonly employeeListRoute = '/employees';

  readonly editingId = signal<string | null>(null);
  readonly state = signal<PageState>('idle');
  readonly error = signal<FormEditorError | null>(null);
  private readonly loadedEmployee = signal<EmployeeWithInEffectAssignmentDto | null>(null);

  readonly form = new FormGroup<EditorForm>({
    identity: createEmployeeIdentityForm(),
    inEffectPlacement: createInEffectPlacementForm(),
  });

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  readonly isEditMode = computed(() => this.editingId() !== null);
  readonly isBusy = computed(() => this.state() === 'loading' || this.state() === 'saving');
  readonly title = computed(() => (this.isEditMode() ? 'Edit employee' : 'New employee'));
  readonly submitLabel = computed(() =>
    this.isEditMode() ? 'Save changes' : 'Create employee',
  );

  /** Passed to the placement component. */
  readonly currentAssignment = computed<EmployeeAssignmentDto | null>(
    () => this.loadedEmployee()?.assignmentInEffect ?? null,
  );
  readonly hasPayroll = computed<boolean>(() => this.currentAssignment()?.hasPayroll ?? false);

  readonly canSubmit = computed(() => {
    this.formValue();
    return this.form.valid && !this.isBusy();
  });

  constructor() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const id = params.get('id');
        this.editingId.set(id);
        if (id) {
          this.loadEmployee(id);
        } else {
          this.state.set('idle');
        }
      });
  }

  private loadEmployee(id: string): void {
    this.state.set('loading');
    this.error.set(null);

    this.employeeService
      .getById(id)
      .pipe(take(1))
      .subscribe({
        next: (emp) => {
          this.loadedEmployee.set(emp);
          this.patchFormFromEmployee(emp);
          this.state.set('idle');
        },
        error: (err) => {
          this.error.set(err?.message ?? 'Failed to load employee');
          this.state.set('error');
        },
      });
  }

  private patchFormFromEmployee(emp: EmployeeWithInEffectAssignmentDto): void {
    this.form.controls.identity.patchValue({
      matricule: emp.matricule,
      fullName: emp.name,
      email: emp.email,
      dateOfBirth: emp.birthDate,
      countryOfOrigin: emp.originCountry?.id ?? null,
    });

    const a = emp.assignmentInEffect;
    const current = this.form.controls.inEffectPlacement.controls.current;
    current.patchValue({
      employmentType: a?.employmentType ?? null,
      departmentId: a?.department.id ?? null,
      roleId: a?.role.id ?? null,
      assignmentCountryId: a?.country.id ?? null,
      payrollFrequency: a?.payrollFrequency ?? null,
      startDate: a?.startDate ?? null,
      endDate: a?.endDate ?? null,
    });
  }

  onSubmit(): void {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    this.state.set('saving');
    this.error.set(null);
    this.clearServerErrors();

    const payload = this.buildSaveRequest();

    this.employeeService
      .save(payload)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.state.set('success');
          this.router.navigate([this.employeeListRoute]);
        },
        error: (err: FormEditorError) => {
          this.error.set(err);
          this.applyServerErrors(err.errors);
          this.state.set('error');
        },
      });
  }

  onCancel(): void {
    this.router.navigate([this.employeeListRoute]);
  }

  // ------------------------------------------------------------------
  // Payload
  // ------------------------------------------------------------------

  private buildSaveRequest(): EmployeeSaveRequest {
    const v = this.form.getRawValue();
    const identity = v.identity;
    const placementForm = this.form.controls.inEffectPlacement;
    const current = placementForm.controls.current;
    const next = placementForm.controls.next;

    const previous: EmployeeAssignmentDto | null = this.currentAssignment();
    const nextActive = next.enabled;

    let assignment: EmployeeAssignmentSaveRequest | null = null;
    let toClose: EmployeeAssignmentToCloseRequest | null = null;

    if (!nextActive) {
      // No new assignment — update the current one (id preserved).
      const c = current.getRawValue();
      if (
        c.employmentType !== null &&
        c.departmentId !== null &&
        c.roleId !== null &&
        c.assignmentCountryId !== null &&
        c.payrollFrequency !== null &&
        c.startDate !== null
      ) {
        assignment = {
          id: previous?.id ?? null,
          countryId: c.assignmentCountryId,
          departmentId: c.departmentId,
          roleId: c.roleId,
          employmentType: c.employmentType,
          payrollFrequency: c.payrollFrequency,
          startDate: c.startDate,
          endDate: c.endDate,
        };
      }
    } else {
      // New assignment added — create it, close the previous one.
      const n = next.getRawValue();
      if (
        n.employmentType !== null &&
        n.departmentId !== null &&
        n.roleId !== null &&
        n.assignmentCountryId !== null &&
        n.payrollFrequency !== null &&
        n.startDate !== null
      ) {
        assignment = {
          id: null,
          countryId: n.assignmentCountryId,
          departmentId: n.departmentId,
          roleId: n.roleId,
          employmentType: n.employmentType,
          payrollFrequency: n.payrollFrequency,
          startDate: n.startDate,
          endDate: n.endDate,
        };
      }
      if (previous?.id) {
        const c = current.getRawValue();
        // The group validator guarantees this is set when next is enabled.
        toClose = { id: previous.id, endDate: c.endDate! };
      }
    }

    return {
      id: this.editingId(),
      matricule: identity.matricule,
      name: identity.fullName,
      email: identity.email,
      birthDate: identity.dateOfBirth,
      originCountryId: identity.countryOfOrigin,
      assignmentInEffect: assignment,
      toClose,
    };
  }

  // ------------------------------------------------------------------
  // Server errors → form
  // ------------------------------------------------------------------

  private applyServerErrors(errors: FieldError[]): void {
    for (const e of errors) {
      const ctrl = this.resolveControl(e.field);
      if (!ctrl) continue;
      const existing = ctrl.errors ?? {};
      ctrl.setErrors({ ...existing, serverError: e.message });
    }
  }

  private resolveControl(field: string): import('@angular/forms').AbstractControl | null {
    if (field.includes('.')) {
      const ctrl = this.form.get(field);
      if (ctrl) return ctrl;
      // Try the current-assignment path for bare assignment fields.
      const current = this.form.controls.inEffectPlacement.controls.current;
      if (current.contains(field)) return current.get(field);
      const next = this.form.controls.inEffectPlacement.controls.next;
      if (next.contains(field)) return next.get(field);
      return null;
    }

    const identity = this.form.controls.identity;
    if (identity.contains(field)) return identity.get(field);

    const current = this.form.controls.inEffectPlacement.controls.current;
    if (current.contains(field)) return current.get(field);

    const next = this.form.controls.inEffectPlacement.controls.next;
    if (next.contains(field)) return next.get(field);

    return null;
  }

  private clearServerErrors(): void {
    const sections = [
      this.form.controls.identity,
      this.form.controls.inEffectPlacement.controls.current,
      this.form.controls.inEffectPlacement.controls.next,
    ];
    for (const section of sections) {
      for (const ctrl of Object.values(section.controls)) {
        if (ctrl.errors && 'serverError' in ctrl.errors) {
          const { serverError, ...rest } = ctrl.errors;
          ctrl.setErrors(Object.keys(rest).length ? rest : null);
        }
      }
    }
  }
}
