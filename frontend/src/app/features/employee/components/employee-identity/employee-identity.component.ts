import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
  OnInit
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CountryService } from '../../../country/services/country.service';
import { CountryData } from '../../../country/models/country.model';

/** Controls owned by this section — the parent composes them into the full form. */
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
export class EmployeeIdentityComponent implements OnInit{
  private readonly destroyRef = inject(DestroyRef);
  private readonly countryService = inject(CountryService);

  /** Parent form group — the parent must supply the identity controls. */
  readonly formGroup = input.required<FormGroup<EmployeeIdentityForm>>();

  /** Countries from the backend. */
  readonly countries = signal<CountryData[]>([]);

  /**
   * Snapshot of the form value as a signal so computed() reacts to changes.
   * Angular forms are RX-based; they are not signals.
   */
  private readonly formValue = signal<EmployeeIdentityValue | null>(null);

  /** Age derived from dateOfBirth (null if no DOB or invalid). */
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

  /** The selected country (for flag + ISO display in the trigger). */
  readonly selectedCountry = computed<CountryData | null>(() => {
    const id = this.formValue()?.countryOfOrigin;
    if (!id) return null;
    return this.countries().find(c => c.id === id) ?? null;
  });

  constructor() {
    this.countryService.search()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(res => this.countries.set(res.content));
  }

  ngOnInit(): void {
    const group = this.formGroup();
    this.formValue.set(group.getRawValue());

    group.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(v => this.formValue.set(v as any));
  }

  /** Turns an ISO country code into a flag emoji. */
  countryFlag(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '';
    const base = 0x1F1E6;
    return String.fromCodePoint(
      base + (code.charCodeAt(0) - 65),
      base + (code.charCodeAt(1) - 65),
    );
  }
}

/** Factory for the identity controls. The parent uses this to build its form. */
export function createEmployeeIdentityForm(): FormGroup<EmployeeIdentityForm> {
  return new FormGroup<EmployeeIdentityForm>({
    matricule: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(32)],
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
      validators: [Validators.required],
    }),
    countryOfOrigin: new FormControl<string | null>(null),
  });
}
