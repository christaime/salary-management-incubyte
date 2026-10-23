import {
  Component,
  ElementRef,
  HostListener,
  ViewChild,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl, ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, startWith, switchMap, catchError, map, of } from 'rxjs';
import { PayrollPeriodDto, PayrollPeriodCriteria } from '../../models';
import { PayrollPeriodsService } from '../../services/payroll-periods.service';

export interface PeriodPayload {
  year: number;
  period: PayrollPeriodDto;
}

@Component({
  selector: 'app-payroll-period-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatIconModule],
  templateUrl: './payroll-period-picker.component.html',
  styleUrls: ['./payroll-period-picker.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PayrollPeriodPickerComponent),
      multi: true,
    },
  ],
})
export class PayrollPeriodPickerComponent implements ControlValueAccessor {
  private readonly periodService: PayrollPeriodsService = inject(PayrollPeriodsService);

  // --- Inputs (@Input) ---
  readonly required = input<boolean>(false);
  readonly label = input<string>("Payroll Cycle Period");
  readonly yearRange = input<[number, number]>([2021, 2026]);
  readonly selectedYear = input<number | null>(null);
  readonly auditBadgeEnabled = input<boolean>(true);
  readonly initialFilter = input<PayrollPeriodCriteria>({});

  // --- Outputs (@Output) ---
  readonly yearChange = output<number>();
  readonly periodChange = output<PeriodPayload>();
  readonly cleared = output<void>();

  // --- Internal State (Signals) ---
  @ViewChild('periodInput') periodInputElement?: ElementRef<HTMLInputElement>;
  @ViewChild('pickerContainer') pickerContainer?: ElementRef<HTMLDivElement>;

  readonly internalYear = signal<number | null>(null);
  readonly isYearPopoverOpen = signal<boolean>(false);
  readonly isPeriodDropdownOpen = signal<boolean>(false);

  // Writable local signal synced via ControlValueAccessor
  readonly activeSelectedPeriod = signal<PayrollPeriodDto | null>(null);
  readonly searchControl = new FormControl<string>('');

  // --- Validation State ---
  readonly touched = signal<boolean>(false);
  readonly isInvalid = computed(() => {
    return this.required() && this.touched() && !this.activeSelectedPeriod();
  });

  // --- ControlValueAccessor Callbacks ---
  private onChange: (value: PayrollPeriodDto | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(obj: PayrollPeriodDto | null): void {
    if (obj) {
      this.activeSelectedPeriod.set(obj);
      if (obj.startDate) {
        this.internalYear.set(this.getYear(obj.startDate));
      }
    } else {
      this.activeSelectedPeriod.set(null);
    }
  }

  registerOnChange(fn: (value: PayrollPeriodDto | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState?(isDisabled: boolean): void {
    // Optional: handle disabled styling/state if needed
  }

  // --- Computed Year Short Strings for Header Badge ---
  readonly minYearShort = computed(() => String(this.yearRange()[0]).slice(2));
  readonly maxYearShort = computed(() => String(this.yearRange()[1]).slice(2));

  // --- API Dynamic Search Pipeline ---
  private readonly year$ = toObservable(this.internalYear);
  private readonly search$ = this.searchControl.valueChanges.pipe(
    startWith(''),
    debounceTime(300),
    distinctUntilChanged()
  );

  readonly filteredPeriods = toSignal(
    this.year$.pipe(
      switchMap((year) =>
        this.search$.pipe(
          switchMap((query) => {
            const baseCriteria = this.initialFilter();
            const startDateFilter = year ? `${year}-01-01` : baseCriteria.startDate;
            const endDateFilter = year ? `${year}-12-31` : baseCriteria.endDate;

            return this.periodService.search({
              ...baseCriteria,
              keyword: query?.trim() || baseCriteria.keyword || undefined,
              startDate: startDateFilter,
              endDate: endDateFilter,
              size: 50,
            }).pipe(
              map((page) => page.content ?? []),
              catchError(() => of([]))
            );
          })
        )
      )
    ),
    { initialValue: [] }
  );

  constructor() {
    effect(() => {
      const yr = this.selectedYear();
      if (yr !== undefined && yr !== null) {
        this.internalYear.set(yr);
      }
    });
  }

  getYear(startDate: string): number {
    return startDate ? parseInt(startDate.split('-')[0], 10) : new Date().getFullYear();
  }

  readonly availableYears = computed(() => {
    const [minYear, maxYear] = this.yearRange();
    const years: { year: number; isDisabled: boolean; isCurrent: boolean }[] = [];
    const currentCalendarYear = new Date().getFullYear();

    for (let y = minYear - 1; y <= maxYear + 1; y++) {
      years.push({
        year: y,
        isDisabled: y < minYear || y > maxYear,
        isCurrent: y === currentCalendarYear,
      });
    }
    return years;
  });

  toggleYearPopover(event?: MouseEvent): void {
    if (event) event.stopPropagation();
    this.isYearPopoverOpen.update((v) => !v);
    this.isPeriodDropdownOpen.set(false);
  }

  selectYear(year: number, isDisabled: boolean): void {
    if (isDisabled) return;
    this.internalYear.set(year);
    this.yearChange.emit(year);
    this.isYearPopoverOpen.set(false);

    const currentSelected = this.activeSelectedPeriod();
    if (currentSelected && this.getYear(currentSelected.startDate) !== year) {
      this.activeSelectedPeriod.set(null);
      this.onChange(null);
    }

    setTimeout(() => {
      this.periodInputElement?.nativeElement.focus();
      this.isPeriodDropdownOpen.set(true);
    }, 100);
  }

  clearYearRange(event?: MouseEvent): void {
    if (event) event.stopPropagation();
    this.internalYear.set(null);
    this.activeSelectedPeriod.set(null);
    this.onChange(null);
    this.isYearPopoverOpen.set(false);
    this.cleared.emit();
  }

  onPeriodInputFocus(): void {
    this.isPeriodDropdownOpen.set(true);
    this.isYearPopoverOpen.set(false);
  }

  onPeriodSearchInput(): void {
    this.isPeriodDropdownOpen.set(true);
  }

  selectPeriod(period: PayrollPeriodDto): void {
    this.activeSelectedPeriod.set(period);
    this.internalYear.set(this.getYear(period.startDate));
    this.isPeriodDropdownOpen.set(false);
    this.searchControl.setValue('');
    this.touched.set(true);

    // Notify reactive forms parent
    this.onChange(period);
    this.onTouched();

    this.periodChange.emit({ year: this.getYear(period.startDate), period });
  }

  clearSelectedPeriod(event?: MouseEvent): void {
    if (event) event.stopPropagation();
    this.activeSelectedPeriod.set(null);
    this.searchControl.setValue('');
    this.touched.set(true);

    // Notify reactive forms parent
    this.onChange(null);
    this.onTouched();

    this.cleared.emit();
    setTimeout(() => {
      this.periodInputElement?.nativeElement.focus();
      this.isPeriodDropdownOpen.set(true);
    }, 50);
  }

  onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (
      this.pickerContainer &&
      !this.pickerContainer.nativeElement.contains(event.target as Node)
    ) {
      this.isYearPopoverOpen.set(false);
      this.isPeriodDropdownOpen.set(false);
    }
  }

  @HostListener('keydown.escape')
  onEscape(): void {
    this.isYearPopoverOpen.set(false);
    this.isPeriodDropdownOpen.set(false);
  }
}
