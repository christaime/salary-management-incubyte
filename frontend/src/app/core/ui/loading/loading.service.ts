import { Injectable, computed, signal } from '@angular/core';

/**
 * Tracks a set of named in-flight operations. Components can bind to
 * `isLoading` (any operation) or `isLoadingFor(key)` (one operation).
 *
 * Signal-based by design — no BehaviorSubject.
 */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly pending = signal<ReadonlySet<string>>(new Set());
  private readonly counter = signal(0);

  /** True when at least one tracked operation is pending. */
  readonly isLoading = computed(() => this.counter() > 0);

  begin(key = 'default'): void {
    this.pending.update((s) => {
      const next = new Set(s);
      next.add(key);
      return next;
    });
    this.counter.update((n) => n + 1);
  }

  end(key = 'default'): void {
    this.pending.update((s) => {
      const next = new Set(s);
      next.delete(key);
      return next;
    });
    this.counter.update((n) => Math.max(0, n - 1));
  }

  isLoadingFor(key: string): boolean {
    return this.pending().has(key);
  }
}
