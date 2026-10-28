import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';

@Component({
  selector: 'app-paginator',
  standalone: true,
  imports: [CommonModule, MatPaginatorModule],
  templateUrl: './paginator.component.html',
  styleUrl: './paginator.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginatorComponent {
  /** Total number of matching records across all pages. */
  readonly totalElements = input.required<number>();
  /** Current page index (0-based). */
  readonly page = input.required<number>();
  /** Current page size. */
  readonly size = input.required<number>();
  /** Label shown after "of N" — e.g. "employees", "rows", "results". */
  readonly unitLabel = input.required<string>();

  readonly pageChange = output<{ page: number; size: number }>();

  /** `[start, end]` 1-based range displayed in the summary. */
  readonly range = computed<[number, number]>(() => {
    const total = this.totalElements();
    if (total === 0) return [0, 0];
    const start = this.page() * this.size() + 1;
    const end = Math.min(start + this.size() - 1, total);
    return [start, end];
  });

  /** Total pages (0 if the dataset is empty). */
  readonly totalPages = computed<number>(() => {
    const total = this.totalElements();
    const size = this.size();
    return size > 0 ? Math.ceil(total / size) : 0;
  });

  /** Passed to `<mat-paginator>` as `[length]`. */
  readonly length = computed(() => this.totalElements());

  onPageEvent(event: PageEvent): void {
    this.pageChange.emit({ page: event.pageIndex, size: event.pageSize });
  }

  formatNumber(n: number): string {
    return n.toLocaleString('en-US');
  }
}
