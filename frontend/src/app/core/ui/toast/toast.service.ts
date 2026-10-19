import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  /** Milliseconds until auto-dismiss; `0` means sticky. */
  durationMs: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 0;
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  /** Default auto-dismiss duration for non-sticky toasts. */
  defaultDurationMs = 5000;

  success(message: string, durationMs = this.defaultDurationMs): void {
    this.push('success', message, durationMs);
  }

  error(message: string, durationMs = this.defaultDurationMs): void {
    this.push('error', message, durationMs);
  }

  info(message: string, durationMs = this.defaultDurationMs): void {
    this.push('info', message, durationMs);
  }

  warning(message: string, durationMs = this.defaultDurationMs): void {
    this.push('warning', message, durationMs);
  }

  dismiss(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }

  clear(): void {
    this._toasts.set([]);
  }

  private push(kind: ToastKind, message: string, durationMs: number): void {
    const toast: Toast = { id: ++this.nextId, kind, message, durationMs };
    this._toasts.update((list) => [...list, toast]);

    if (durationMs > 0) {
      setTimeout(() => this.dismiss(toast.id), durationMs);
    }
  }
}
