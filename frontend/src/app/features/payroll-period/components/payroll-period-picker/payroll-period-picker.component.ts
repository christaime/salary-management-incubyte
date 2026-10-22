import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  ViewChild,
  computed,
  forwardRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';

import { PayrollPeriodDto } from '../../models/payroll-period.model';
import { PayrollPeriodCriteria } from '../../models/payroll-period-criteria.model';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';

@Component({
  selector: 'app-payroll-period-picker',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatMenuModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './payroll-period-picker.component.html',
  styleUrl: './payroll-period-picker.component.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PayrollPeriodPickerComponent),
      multi: true,
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => PayrollPeriodPickerComponent),
      multi: true,
    },
  ],
})
export class PayrollPeriodPickerComponent implements ControlValueAccessor, Validator {
  private readonly periodsService = inject(PayrollPeriodsService);

  // --- Inputs / Outputs ---------------------------------------------------
  readonly label = input<string>('Period');
  readonly startYearAllowed = input<number>(new Date().getFullYear() - 5);
  readonly initialFilter = input<PayrollPeriodCriteria>({});
  readonly isRequired = input<boolean>(false);

  readonly yearSelected = output<number>();
  readonly periodSelected = output<PayrollPeriodDto>();

  // --- Internal state -----------------------------------------------------
  readonly searchText = signal<string>('');
  readonly selectedYear = signal<number | null>(null);
  readonly selectedPeriod = signal<PayrollPeriodDto | null>(null);
  readonly touched = signal<boolean>(false);

  // Generate a list of years from current year down to startYearAllowed
  readonly availableYears = computed(() => {
    const currentYear = new Date().getFullYear();
    const startYear = this.startYearAllowed();
    const years: number[] = [];
    for (let y = currentYear; y >= startYear; y--) {
      years.push(y);
    }
    return years;
  });

  // --- Search Logic -------------------------------------------------------
  private readonly resultsPage = toSignal(
    toObservable(
      computed(() => {
        const text = this.searchText();
        const year = this.selectedYear();

        const criteria: PayrollPeriodCriteria = {
          ...this.initialFilter(),
          size: 20,
        };

        if (text) criteria.keyword = text;

        if (year != null) {
          criteria.startDate = `${year}-01-01`;
          criteria.endDate = `${year}-12-31`;
        }

        return criteria;
      }),
    ).pipe(
      debounceTime(300),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      switchMap((criteria) => {
        // Allow fetch if either a search keyword or a year filter is provided
        if (!criteria.keyword && !criteria.startDate) {
          return of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, empty: true });
        }
        return this.periodsService.list(criteria).pipe(
          catchError(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, empty: true }))
        );
      }),
    ),
    {
      initialValue: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, empty: true },
    },
  );

  readonly results = computed<PayrollPeriodDto[]>(() => this.resultsPage()?.content ?? []);

  // --- CVA plumbing -------------------------------------------------------
  private onChange: (v: PayrollPeriodDto | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: PayrollPeriodDto | null): void {
    this.selectedPeriod.set(value);
    this.searchText.set(value?.name ?? '');
    if (value) {
      const year = Number(value.startDate.slice(0, 4));
      this.selectedYear.set(year);
    }
    // Note: Removed clearing of selectedYear here so the year stays when period is deselected/nullified
  }

  registerOnChange(fn: (v: PayrollPeriodDto | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(_disabled: boolean): void {}

  validate(_control: AbstractControl): ValidationErrors | null {
    if (this.isRequired() && !this.selectedPeriod()) {
      return { required: true };
    }
    return null;
  }

  // --- View interactions --------------------------------------------------
  @ViewChild('searchInput', { static: true }) searchInput?: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger) autocompleteTrigger?: MatAutocompleteTrigger;

  onInput(value: string): void {
    this.searchText.set(value);
    if (!value) {
      this.selectedPeriod.set(null);
      this.onChange(null);
    }
  }

  onSelectPeriod(period: PayrollPeriodDto): void {
    this.selectedPeriod.set(period);
    this.searchText.set(period.name);
    this.onChange(period);
    this.periodSelected.emit(period);
    this.markAsTouched();
  }

  onBlur(): void {
    this.markAsTouched();
    const typed = this.searchText().trim();
    const selected = this.selectedPeriod();
    if (selected && typed !== selected.name) {
      this.searchText.set(selected.name);
    } else if (!typed && selected) {
      this.selectedPeriod.set(null);
      this.onChange(null);
    }
  }

  onOpenPicker(): void {
    setTimeout(() => this.autocompleteTrigger?.openPanel());
  }

  selectYear(year: number): void {
    this.selectedYear.set(year);
    this.yearSelected.emit(year);
    setTimeout(() => this.autocompleteTrigger?.openPanel());
  }

  clearYear(event: Event): void {
    event.stopPropagation();
    this.selectedYear.set(null);
    setTimeout(() => this.autocompleteTrigger?.openPanel());
  }

  clear(): void {
    this.selectedPeriod.set(null);
    this.searchText.set('');
    // Note: Removed selectedYear.set(null) here so clicking the clear button preserves the chosen year
    this.onChange(null);
  }

  private markAsTouched(): void {
    if (!this.touched()) {
      this.touched.set(true);
      this.onTouched();
    }
  }

  displayFn = (period: PayrollPeriodDto | null): string => period?.name ?? '';
}
