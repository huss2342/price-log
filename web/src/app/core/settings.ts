import { Injectable, signal, effect } from '@angular/core';

const KEY = 'pricelog.settings';

interface Persisted {
  apiBase: string;
  apiKey: string;
  /** Remembered so the common case is one tap: you shop the same places. */
  defaultStoreId: number | null;
}

const DEFAULTS: Persisted = {
  apiBase: 'http://localhost:8080',
  apiKey: '',
  defaultStoreId: null,
};

@Injectable({ providedIn: 'root' })
export class Settings {
  readonly apiBase = signal(DEFAULTS.apiBase);
  readonly apiKey = signal(DEFAULTS.apiKey);
  readonly defaultStoreId = signal<number | null>(DEFAULTS.defaultStoreId);

  constructor() {
    this.load();
    // Every change writes straight through, so a reload never loses the config.
    effect(() => {
      const value: Persisted = {
        apiBase: this.apiBase(),
        apiKey: this.apiKey(),
        defaultStoreId: this.defaultStoreId(),
      };
      localStorage.setItem(KEY, JSON.stringify(value));
    });
  }

  /** True once the app knows where the API lives. */
  isConfigured(): boolean {
    return this.apiBase().trim().length > 0;
  }

  private load(): void {
    const raw = localStorage.getItem(KEY);
    if (!raw) return;
    try {
      const parsed = { ...DEFAULTS, ...(JSON.parse(raw) as Partial<Persisted>) };
      this.apiBase.set(parsed.apiBase);
      this.apiKey.set(parsed.apiKey);
      this.defaultStoreId.set(parsed.defaultStoreId);
    } catch {
      localStorage.removeItem(KEY);
    }
  }
}
