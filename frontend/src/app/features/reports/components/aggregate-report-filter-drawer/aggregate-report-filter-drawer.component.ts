import { CommonModule } from '@angular/common';
import { Component, HostListener, inject, input, output, signal } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { CountryData } from '../../../country/models/country.model';
import { CountryService } from '../../../country/services/country.service';
import { DepartmentData } from '../../../department/models/department.model';
import { DepartmentService } from '../../../department/services/department.service';
import { RoleData } from '../../../role/models/role.model';
import { RoleService } from '../../../role/services/role.service';
import { EMPLOYMENT_TYPE_LABELS, EMPLOYMENT_TYPES, EmploymentType } from '@features/employee/models';

// We type the form as the full parent form, but we only use the filter controls
export type FilterFormGroup = FormGroup<{
  countries: FormControl<string[]>;
  departments: FormControl<string[]>;
  roles: FormControl<string[]>;
  employmentTypes: FormControl<EmploymentType[]>;
}>;

@Component({
  selector: 'app-aggregate-report-filter-drawer',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './aggregate-report-filter-drawer.component.html',
})
export class AggregateReportFilterDrawerComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  // The parent form is of type FormGroup<any>, so we accept it and cast internally
  readonly form = input.required<FormGroup>();

  readonly applied = output<void>();

  readonly isOpen = signal<boolean>(false);

  // Lookup data
  readonly countries = signal<CountryData[]>([]);
  readonly departments = signal<DepartmentData[]>([]);
  readonly roles = signal<RoleData[]>([]);

  readonly employmentTypes: ReadonlyArray<EmploymentType> = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels: Readonly<Record<EmploymentType, string>> = EMPLOYMENT_TYPE_LABELS;

  // Display texts
  readonly countryDisplayText = signal<string>('');
  readonly departmentDisplayText = signal<string>('');
  readonly roleDisplayText = signal<string>('');
  readonly employmentTypeDisplayText = signal<string>('');

  readonly countryTooltip = signal<string>('');
  readonly departmentTooltip = signal<string>('');
  readonly roleTooltip = signal<string>('');

  constructor() {
    this.fetchData();
  }

  open(): void {
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
  }

  apply(): void {
    this.applied.emit();
    this.close();
  }

  clearAll(): void {
    this.form().patchValue({
      countries: [],
      departments: [],
      roles: [],
      employmentTypes: [],
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) {
      this.close();
    }
  }

  private fetchData(): void {
    this.countryService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
      this.countries.set(res.content);
      this.updateCountryDisplay(this.form().controls['countries'].value);
    });

    this.departmentService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
      this.departments.set(res.content);
      this.updateDepartmentDisplay(this.form().controls['departments'].value);
    });

    this.roleService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
      this.roles.set(res.content);
      this.updateRoleDisplay(this.form().controls['roles'].value);
    });

    setTimeout(() => {
      const f = this.form().controls;
      this.updateCountryDisplay(f['countries'].value);
      this.updateDepartmentDisplay(f['departments'].value);
      this.updateRoleDisplay(f['roles'].value);
      this.updateEmploymentTypeDisplay(f['employmentTypes'].value);

      f['countries'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => this.updateCountryDisplay(v));
      f['departments'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => this.updateDepartmentDisplay(v));
      f['roles'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => this.updateRoleDisplay(v));
      f['employmentTypes'].valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => this.updateEmploymentTypeDisplay(v));
    });
  }

  private updateCountryDisplay(ids: string[] | null): void {
    if (!ids || ids.length === 0) {
      this.countryDisplayText.set(`All ${this.countries().length} countries`);
      this.countryTooltip.set('');
      return;
    }
    const names = ids.map((id) => this.countries().find((c) => c.id === id)?.name ?? id);
    this.countryDisplayText.set(`${names.join(', ')}`);
    this.countryTooltip.set(this.makeFilterTooltip(ids, this.countries()));
  }

  private updateDepartmentDisplay(ids: string[] | null): void {
    if (!ids || ids.length === 0) {
      this.departmentDisplayText.set(`All ${this.departments().length} departments`);
      this.departmentTooltip.set('');
      return;
    }
    const names = ids.map((id) => this.departments().find((d) => d.id === id)?.name ?? id);
    this.departmentDisplayText.set(`${names.join(', ')}`);
    this.departmentTooltip.set(this.makeFilterTooltip(ids, this.departments()));
  }

  private updateRoleDisplay(ids: string[] | null): void {
    if (!ids || ids.length === 0) {
      this.roleDisplayText.set(`All ${this.roles().length} roles`);
      this.roleTooltip.set('');
      return;
    }
    const names = ids.map((id) => this.roles().find((r) => r.id === id)?.name ?? id);
    this.roleDisplayText.set(`${names.join(', ')}`);
    this.roleTooltip.set(this.makeFilterTooltip(ids, this.roles()));
  }

  private updateEmploymentTypeDisplay(values: EmploymentType[] | null): void {
    if (!values || values.length === 0) {
      this.employmentTypeDisplayText.set(`All ${this.employmentTypes.length} employment types`);
      return;
    }
    const names = values.map((v) => this.employmentTypeLabels[v]);
    this.employmentTypeDisplayText.set(`${names.join(', ')}`);
  }

  private makeFilterTooltip(ids: string[] | null | undefined, options: ReadonlyArray<{ id: string; name: string }>): string {
    if (!ids || ids.length <= 2) return '';
    return ids.map((id) => options.find((o) => o.id === id)?.name ?? id).join(', ');
  }

  employmentTypeLabel(value: EmploymentType): string {
    return this.employmentTypeLabels[value];
  }
}
