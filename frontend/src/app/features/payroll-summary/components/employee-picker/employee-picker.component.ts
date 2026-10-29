import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { MatIconModule } from '@angular/material/icon';

import { EmployeeService } from '../../../employee/services/employee.service';
import { EmployeeWithInEffectAssignmentDto } from '@features/employee/models';

/**
 * Debounced employee picker.
 * Searching fires automatically 300 ms after the keyword changes and is
 * at least 3 chars long. No explicit Search button; Enter still works as a
 * "search now" shortcut.
 * The selected employee replaces the search input in place. Clearing the
 * selection restores the search input.
 */
@Component({
  selector: 'app-employee-picker',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
  templateUrl: './employee-picker.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmployeePickerComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly employeeService = inject(EmployeeService);

  readonly selectedChange = output<EmployeeWithInEffectAssignmentDto | null>();

  readonly keyword = new FormControl<string>('', { nonNullable: true });
  readonly results = signal<EmployeeWithInEffectAssignmentDto[]>([]);
  readonly searching = signal(false);
  readonly searchError = signal<string | null>(null);
  readonly selected = signal<EmployeeWithInEffectAssignmentDto | null>(null);

  /** Minimum characters before the debounced fetch fires. */
  private static readonly MIN_CHARS = 3;

  /**
   * Signal mirror of the keyword value. `FormControl.value` is a plain getter,
   * so a `computed` that reads it would never recompute. Mirroring into a
   * signal keeps `canSearch` reactive.
   */
  private readonly keywordValue = toSignal(
    this.keyword.valueChanges.pipe(startWith(this.keyword.value)),
    { initialValue: this.keyword.value },
  );

  readonly canSearch = computed(
    () => this.keywordValue().trim().length >= EmployeePickerComponent.MIN_CHARS,
  );

  constructor() {
    this.keyword.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        filter((q) => q.trim().length >= EmployeePickerComponent.MIN_CHARS),
        tap(() => {
          this.searching.set(true);
          this.searchError.set(null);
        }),
        switchMap((q) =>
          this.employeeService.search({ keyword: q.trim(), page: 0, size: 5 }).pipe(
            catchError((err) => {
              this.searchError.set(err?.message ?? 'Search failed');
              return of({ content: [] as EmployeeWithInEffectAssignmentDto[] });
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        this.results.set(page.content ?? []);
        this.searching.set(false);
      });
  }

  /** Force a fetch now (Enter key). Debounce still runs but a fresh fetch is scheduled. */
  onSearch(): void {
    const q = this.keyword.value.trim();
    if (q.length < EmployeePickerComponent.MIN_CHARS) return;
    // Re-emitting the same value bypasses distinctUntilChanged only if the
    // value actually differs; Enter on an unchanged query is a no-op.
    // For an explicit "search now" we bypass the debounce by triggering the
    // same pipeline with a microtask-level re-push.
    this.keyword.setValue(q);
    this.triggerSearchNow(q);
  }

  private triggerSearchNow(q: string): void {
    this.searching.set(true);
    this.searchError.set(null);
    this.employeeService
      .search({ keyword: q, page: 0, size: 5 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.results.set(page.content ?? []);
          this.searching.set(false);
        },
        error: (err) => {
          this.results.set([]);
          this.searching.set(false);
          this.searchError.set(err?.message ?? 'Search failed');
        },
      });
  }

  selectEmployee(e: EmployeeWithInEffectAssignmentDto): void {
    this.selected.set(e);
    this.results.set([]);
    this.keyword.setValue('');
    this.selectedChange.emit(e);
  }

  clearSelection(): void {
    this.selected.set(null);
    this.selectedChange.emit(null);
  }
}
