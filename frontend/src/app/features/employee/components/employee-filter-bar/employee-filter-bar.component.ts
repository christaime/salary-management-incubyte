import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed , toSignal} from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { CountryService } from '../../../country/services/country.service';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleService } from '../../../role/services/role.service';
import {
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_LABELS,
  EmploymentType,
  EmployeeCriteria,
} from '../../models';

export interface EmployeeFilterValue {
  keyword: string | null;
  assignmentCountryId: string | null;
  departmentId: string | null;
  roleId: string | null;
  employmentType: EmploymentType | null;
}

interface Option {
  id: string;
  name: string;
}

@Component({
  selector: 'app-employee-filter-bar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './employee-filter-bar.component.html',
  styleUrl: './employee-filter-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeFilterBarComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  /** Emitted whenever a filter changes. The page composes it into EmployeeCriteria. */
  readonly filterChange = output<EmployeeFilterValue>();

  readonly form = new FormGroup({
    keyword: new FormControl<string>('', { nonNullable: true }),
    assignmentCountryId: new FormControl<string | null>(null),
    departmentId: new FormControl<string | null>(null),
    roleId: new FormControl<string | null>(null),
    employmentType: new FormControl<EmploymentType | null>(null),
  });

  readonly countries = signal<Option[]>([]);
  readonly departments = signal<Option[]>([]);
  readonly roles = signal<Option[]>([]);
  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;

  // Convert form valueChanges into a tracked signal
  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  /** True when at least one filter is set (used to enable/disable Reset). */
  readonly hasActiveFilters = computed(() => {
    const v = this.formValue();
    return (
      v.keyword !== '' ||
      v.assignmentCountryId !== null ||
      v.departmentId !== null ||
      v.roleId !== null ||
      v.employmentType !== null
    );
  });

  constructor() {
    this.loadLookups();

    // Debounced keyword → filterChange
    this.form.controls.keyword.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.emit());

    // Immediate dropdowns → filterChange
    this.form.controls.assignmentCountryId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.emit());
    this.form.controls.departmentId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.emit());
    this.form.controls.roleId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.emit());
    this.form.controls.employmentType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.emit());
  }

  onReset(): void {
    this.form.reset({
      keyword: '',
      assignmentCountryId: null,
      departmentId: null,
      roleId: null,
      employmentType: null,
    });
    // The subscriptions fire and emit; no explicit emit needed.
  }

  employmentTypeLabel(value: EmploymentType): string {
    return this.employmentTypeLabels[value];
  }

  private emit(): void {
    const v = this.form.getRawValue();
    this.filterChange.emit({
      keyword: v.keyword || null,
      assignmentCountryId: v.assignmentCountryId,
      departmentId: v.departmentId,
      roleId: v.roleId,
      employmentType: v.employmentType,
    });
  }

  private loadLookups(): void {
    this.countryService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.countries.set(res.content.map(c => ({ id: c.id, name: c.name }))));

    this.departmentService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.departments.set(res.content.map(d => ({ id: d.id, name: d.name }))));

    this.roleService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.roles.set(res.content.map(r => ({ id: r.id, name: r.name }))));
  }
}
