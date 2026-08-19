import { Injectable, signal, effect } from '@angular/core';

const KEY = 'pricelog.settings';

/**
 * The deployed API. Baked in so a fresh install is usable without typing a URL;
 * still overridable in Settings, and localhost wins during development.
 */
const HOSTED_API = 'https://price-log-api.greencoast-8c619113.eastus.azurecontainerapps.io';

function defaultApiBase(): string {
  const host = location.hostname;
  return host === 'localhost' || host === '127.0.0.1' ? 'http://localhost:8080' : HOSTED_API;
}

interface Persisted {
  apiBase: string;
  apiKey: string;
  /** Remembered so the common case is one tap: you shop the same places. */
  defaultStoreId: number | null;
}

const DEFAULTS: Persisted = {
  apiBase: '',
  apiKey: '',
  defaultStoreId: null,
};

@Injectable({ providedIn: 'root' })
export class Settings {
  readonly apiBase = signal(defaultApiBase());
  readonly apiKey = signal(DEFAULTS.apiKey);
  readonly defaultStoreId = signal<number | null>(DEFAULTS.defaultStoreId);

  constructor() {
    this.load();
    this.applyLinkConfig();
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

  /**
   * Reads /config.json, which the deploy writes into the published site but
   * never into git. The site sits behind a GitHub sign-in restricted to a
   * single account, so a key served from there can only ever be downloaded by
   * that account — which is what makes shipping it safe.
   *
   * Anything already configured on this device wins, so a manual override or a
   * setup link is never silently replaced.
   */
  async loadHostedConfig(): Promise<void> {
    try {
      const response = await fetch('/config.json', { cache: 'no-store' });
      if (!response.ok) return;

      const config = (await response.json()) as Partial<Persisted>;
      if (config.apiBase && !this.storedApiBase) this.apiBase.set(config.apiBase);
      if (config.apiKey && !this.apiKey()) this.apiKey.set(config.apiKey);
    } catch {
      // No hosted config, or offline. Settings can still be filled in by hand.
    }
  }

  /** True once the app knows where the API lives. */
  isConfigured(): boolean {
    return this.apiBase().trim().length > 0;
  }

  private storedApiBase = false;

  private load(): void {
    const raw = localStorage.getItem(KEY);
    if (!raw) return;
    try {
      const parsed = { ...DEFAULTS, ...(JSON.parse(raw) as Partial<Persisted>) };
      // An older install may have stored a blank or stale localhost address.
      if (parsed.apiBase) {
        this.apiBase.set(parsed.apiBase);
        this.storedApiBase = true;
      }
      this.apiKey.set(parsed.apiKey);
      this.defaultStoreId.set(parsed.defaultStoreId);
    } catch {
      localStorage.removeItem(KEY);
    }
  }

  /**
   * Lets a single link carry the configuration: ?key=...&api=...
   *
   * On iOS a home-screen web app gets its own storage, separate from Safari,
   * so anything typed into Settings in the browser does not follow the app when
   * it is installed. Opening the configuring link from inside the installed app
   * sets it up there too, with no retyping.
   *
   * The parameters are stripped from the address bar immediately so the key
   * does not linger in history or get shared with the URL.
   */
  private applyLinkConfig(): void {
    const params = new URLSearchParams(location.search);
    const api = params.get('api');
    const key = params.get('key');
    if (!api && !key) return;

    if (api) this.apiBase.set(api);
    if (key) this.apiKey.set(key);

    params.delete('api');
    params.delete('key');
    const rest = params.toString();
    history.replaceState({}, '', location.pathname + (rest ? `?${rest}` : ''));
  }
}
