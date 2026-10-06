import { Injectable, signal } from '@angular/core';

export type ToastKind = 'ok' | 'info' | 'error';

export interface ToastAction {
  label: string;
  run: () => void;
}

interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
  action?: ToastAction;
}

/** How long a toast lingers before dismissing itself. */
const AUTO_DISMISS_MS = 4_000;

/**
 * Short messages over the tab bar: "Store added", "Could not reach the API".
 * Rendered by the host in app.html; the auth flows will use this file too.
 */
@Injectable({ providedIn: 'root' })
export class Toast {
  /** Rendered by the host in app.html, oldest first. */
  readonly items = signal<ToastItem[]>([]);

  private nextId = 0;

  /**
   * Shows a message that dismisses itself after a few seconds. A sticky toast
   * with an action — like the update prompt's Reload — stays until the action
   * runs or it is tapped.
   */
  show(message: string, kind: ToastKind = 'ok', opts: { action?: ToastAction; sticky?: boolean } = {}): void {
    const id = ++this.nextId;
    this.items.update((current) => [...current.slice(-2), { id, message, kind, action: opts.action }]);
    if (!opts.sticky) {
      setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
    }
  }

  dismiss(id: number): void {
    this.items.update((current) => current.filter((toast) => toast.id !== id));
  }
}
