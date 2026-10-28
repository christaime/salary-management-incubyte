import { CommonModule } from '@angular/common';
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
  Validators,
} from '@angular/forms';

import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CountryService } from '../../../country/services/country.service';
import { CountryData } from '../../../country/models/country.model';

export interface EmployeeIdentityForm {
  matricule: FormControl<string>;
  fullName: FormControl<string>;
  email: FormControl<string>;
  dateOfBirth: FormControl<string | null>;
  countryOfOrigin: FormControl<string | null>;
}

export interface EmployeeIdentityValue {
  matricule: string;
  fullName: string;
  email: string;
  dateOfBirth: string | null;
  countryOfOrigin: string | null;
}

/**
 * Rejects dates strictly in the future, compared at local midnight.
 * Returns null when the field is empty or unparseable (so `required`
 * is the only gate for missing values).
 */
export function pastDateValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string | null;
  if (!value) return null;

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return { invalidDate: true };

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  parsed.setHours(0, 0, 0, 0);

  return parsed > today ? { futureDate: true } : null;
}

@Component({
  selector: 'app-employee-identity',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
  ],
  templateUrl: './employee-identity.component.html',
  styleUrl: './employee-identity.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeeIdentityComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);

  readonly formGroup = input.required<FormGroup<EmployeeIdentityForm>>();

  readonly countries = signal<CountryData[]>([]);

  private readonly formValue = signal<EmployeeIdentityValue | null>(null);

  readonly computedAge = computed<number | null>(() => {
    const dob = this.formValue()?.dateOfBirth;
    if (!dob) return null;
    const birth = new Date(dob);
    if (Number.isNaN(birth.getTime())) return null;
    const now = new Date();
    let age = now.getFullYear() - birth.getFullYear();
    const monthDiff = now.getMonth() - birth.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
      age--;
    }
    return age;
  });

  readonly selectedCountry = computed<CountryData | null>(() => {
    const id = this.formValue()?.countryOfOrigin;
    if (!id) return null;
    return this.countries().find((c) => c.id === id) ?? null;
  });

  ngOnInit(): void {
    const group = this.formGroup();

    this.formValue.set(group.getRawValue());

    group.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.formValue.set(v as EmployeeIdentityValue));

    this.countryService
      .search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((res) => this.countries.set(res.content));
  }

  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1f1e6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }

  // ---------- Error helpers ----------

  /** True once the field is invalid and has been touched or modified. */
  showError(name: keyof EmployeeIdentityForm): boolean {
    const ctrl = this.formGroup().controls[name];
    return ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  matriculeError(): string | null {
    const ctrl = this.formGroup().controls.matricule;
    if (!this.showError('matricule')) return null;
    if (ctrl.hasError('serverError')) return ctrl.getError('serverError');
    if (ctrl.hasError('required')) return 'Matricule is required.';
    if (ctrl.hasError('maxlength')) return 'Matricule must be at most 100 characters.';
    return null;
  }

  fullNameError(): string | null {
    const ctrl = this.formGroup().controls.fullName;
    if (!this.showError('fullName')) return null;
    if (ctrl.hasError('serverError')) return ctrl.getError('serverError');
    if (ctrl.hasError('required')) return 'Full name is required.';
    if (ctrl.hasError('maxlength')) return 'Full name must be at most 100 characters.';
    return null;
  }

  emailError(): string | null {
    const ctrl = this.formGroup().controls.email;
    if (!this.showError('email')) return null;
    if (ctrl.hasError('serverError')) return ctrl.getError('serverError');
    if (ctrl.hasError('required')) return 'Email is required.';
    if (ctrl.hasError('maxlength')) return 'Email must be at most 254 characters.';
    if (ctrl.hasError('email')) return 'Email format is invalid.';
    return null;
  }

  dobError(): string | null {
    const ctrl = this.formGroup().controls.dateOfBirth;
    if (!this.showError('dateOfBirth')) return null;
    if (ctrl.hasError('serverError')) return ctrl.getError('serverError');
    if (ctrl.hasError('required')) return 'Date of birth is required.';
    if (ctrl.hasError('futureDate')) return 'Date of birth cannot be in the future.';
    return null;
  }

  countryError(): string | null {
    const ctrl = this.formGroup().controls.countryOfOrigin;
    if (!this.showError('countryOfOrigin')) return null;
    if (ctrl.hasError('serverError')) return ctrl.getError('serverError');
    if (ctrl.hasError('required')) return 'Country of origin is required.';
    return null;
  }
}

export function createEmployeeIdentityForm(): FormGroup<EmployeeIdentityForm> {
  return new FormGroup<EmployeeIdentityForm>({
    matricule: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    dateOfBirth: new FormControl<string | null>(null, {
      validators: [Validators.required, pastDateValidator],
    }),
    countryOfOrigin: new FormControl<string | null>(null, {
      validators: [Validators.required],
    }),
  });
}
