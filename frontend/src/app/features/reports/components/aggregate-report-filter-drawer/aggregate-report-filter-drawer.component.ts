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
  styleUrl: './aggregate-report-filter-drawer.component.scss',
})
export class AggregateReportFilterDrawerComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);
  private readonly departmentService = inject(DepartmentService);
  private readonly roleService = inject(RoleService);

  // Parent form reference passed from parent component
  readonly form = input.required<FormGroup>();

  readonly applied = output<void>();
  readonly isOpen = signal<boolean>(false);

  // Local draft form to stage filter changes before clicking Apply
  readonly localForm = new FormGroup({
    countries: new FormControl<any[]>([], { nonNullable: true }),
    departments: new FormControl<any[]>([], { nonNullable: true }),
    roles: new FormControl<any[]>([], { nonNullable: true }),
    employmentTypes: new FormControl<EmploymentType[]>([], { nonNullable: true }),
  });

  // Lookup data
  readonly countries = signal<CountryData[]>([]);
  readonly departments = signal<DepartmentData[]>([]);
  readonly roles = signal<RoleData[]>([]);

  readonly employmentTypes: ReadonlyArray<EmploymentType> = EMPLOYMENT_TYPES;
  readonly employmentTypeLabels: Readonly<Record<EmploymentType, string>> = EMPLOYMENT_TYPE_LABELS;

  // Local Display Texts & Tooltips for UI preview
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
    // Sync local form with parent form values when drawer opens
    const parentVal = this.form().value;
    this.localForm.patchValue({
      countries: parentVal.countries ?? [],
      departments: parentVal.departments ?? [],
      roles: parentVal.roles ?? [],
      employmentTypes: parentVal.employmentTypes ?? [],
    }, { emitEvent: false });

    this.updateDisplays(this.localForm.value);
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
  }

  apply(): void {
    // Commit local draft values to the parent form only when Apply is clicked
    const val = this.localForm.value;
    this.form().patchValue({
      countries: val.countries,
      departments: val.departments,
      roles: val.roles,
      employmentTypes: val.employmentTypes,
    });
    this.applied.emit();
    this.close();
  }

  clearAll(): void {
    this.localForm.patchValue({
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

  // Custom equality comparison for Material select object matching
  compareRef(o1: any | null, o2: any | null): boolean {
    if (!o1 || !o2) return o1 === o2;
    return o1.id === o2.id || o1.code === o2.code;
  }

  private fetchData(): void {
      this.countryService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
        this.countries.set(res.content);
        this.updateCountryDisplay(this.localForm.getRawValue().countries);
      });

      this.departmentService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
        this.departments.set(res.content);
        this.updateDepartmentDisplay(this.localForm.getRawValue().departments);
      });

      this.roleService.search().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((res) => {
        this.roles.set(res.content);
        this.updateRoleDisplay(this.localForm.getRawValue().roles);
      });

      // Listen to local form changes to update UI preview texts inside the drawer instantly
      this.localForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((val) => {
        this.updateDisplays(val);
      });
    }

    private updateDisplays(val: any): void {
      this.updateCountryDisplay(val?.countries);
      this.updateDepartmentDisplay(val?.departments);
      this.updateRoleDisplay(val?.roles);
      this.updateEmploymentTypeDisplay(val?.employmentTypes);
    }

    private updateCountryDisplay(items: any[] | null | undefined): void {
      if (!items || items.length === 0) {
        this.countryDisplayText.set(`All ${this.countries().length} countries`);
        this.countryTooltip.set('');
        return;
      }
      const names = items.map((item) => item.name);
      this.countryDisplayText.set(names.join(', '));
      this.countryTooltip.set(items.length > 2 ? names.join(', ') : '');
    }

    private updateDepartmentDisplay(items: any[] | null | undefined): void {
      if (!items || items.length === 0) {
        this.departmentDisplayText.set(`All ${this.departments().length} departments`);
        this.departmentTooltip.set('');
        return;
      }
      const names = items.map((item) => item.name);
      this.departmentDisplayText.set(names.join(', '));
      this.departmentTooltip.set(items.length > 2 ? names.join(', ') : '');
    }

    private updateRoleDisplay(items: any[] | null | undefined): void {
      if (!items || items.length === 0) {
        this.roleDisplayText.set(`All ${this.roles().length} roles`);
        this.roleTooltip.set('');
        return;
      }
      const names = items.map((item) => item.name);
      this.roleDisplayText.set(names.join(', '));
      this.roleTooltip.set(items.length > 2 ? names.join(', ') : '');
    }

  private updateEmploymentTypeDisplay(values: EmploymentType[] | null): void {
    if (!values || values.length === 0) {
      this.employmentTypeDisplayText.set(`All ${this.employmentTypes.length} employment types`);
      return;
    }
    const names = values.map((v) => this.employmentTypeLabels[v] ?? v);
    this.employmentTypeDisplayText.set(names.join(', '));
  }

  employmentTypeLabel(value: EmploymentType): string {
    return this.employmentTypeLabels[value] ?? value;
  }
}
