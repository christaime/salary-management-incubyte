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
import { CommonModule } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

import { CountryService } from '../../../country/services/country.service';
import { CountryData } from '../../../country/models/country.model';
import { DepartmentService } from '../../../department/services/department.service';
import { DepartmentData } from '../../../department/models/department.model';
import { RoleService } from '../../../role/services/role.service';
import { RoleData } from '../../../role/models/role.model';

import {
  AssignmentEditorForm,
  AssignmentEditorValue,
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  PAYROLL_PERIOD_FREQUENCY_LABELS,
} from './assignment-form.model';

@Component({
  selector: 'app-assignment-editor-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
  ],
  templateUrl: './assignment-editor-form.component.html',
  styleUrl: './assignment-editor-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentEditorFormComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  readonly formGroup = input.required<FormGroup<AssignmentEditorForm>>();

  /** When true, the form fields are read-only (used by the pill case). */
  readonly readOnly = input<boolean>(false);

  /** Optional label shown above the grid, e.g. "New assignment". */
  readonly heading = input<string | null>(null);

  readonly countries = signal<CountryData[]>([]);
  readonly departments = signal<DepartmentData[]>([]);
  readonly roles = signal<RoleData[]>([]);

  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;
  readonly frequencyLabels = PAYROLL_PERIOD_FREQUENCY_LABELS;

  private readonly formValue = signal<AssignmentEditorValue | null>(null);
  private readonly endBeforeStartSignal = signal<boolean>(false);

  readonly endBeforeStart = computed(() => this.endBeforeStartSignal());

  ngOnInit(): void {
    const group = this.formGroup();

    this.formValue.set(group.getRawValue() as AssignmentEditorValue);
    this.endBeforeStartSignal.set(group.hasError('endBeforeStart'));

    group.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => {
        this.formValue.set(v as AssignmentEditorValue);
        this.endBeforeStartSignal.set(group.hasError('endBeforeStart'));
      });

    group.statusChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() =>
        this.endBeforeStartSignal.set(group.hasError('endBeforeStart')),
      );

    this.countryService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) => this.countries.set(res.content));

    this.departmentService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) => this.departments.set(res.content));

    this.roleService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) => this.roles.set(res.content));
  }

  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1f1e6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }
}
