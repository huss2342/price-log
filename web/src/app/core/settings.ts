import { Injectable, signal, effect } from '@angular/core';
import { CHAINS, type Chain } from './models';

const KEY = 'pricelog.settings';

/**
 * Where the API lives, when nothing has said otherwise.
 *
 * <p>Development points at the local server. Anywhere else it is left empty on
 * purpose: this repo is public, and baking one deployment's hostname in would
 * aim every fork at that same API. A deployed install gets its address from the
 * ?api= setup link, which carries the key it needs anyway, so there is nothing
 * extra to type either way.
 */
function defaultApiBase(): string {
  const host = location.hostname;
  return host === 'localhost' || host === '127.0.0.1' ? 'http://localhost:8080' : '';
}

interface Persisted {
  apiBase: string;
  apiKey: string;
  /** Remembered so the common case is no tap at all: you shop the same place. */
  chain: Chain | null;
}

const DEFAULTS: Persisted = {
  apiBase: '',
  apiKey: '',
  chain: null,
};

@Injectable({ providedIn: 'root' })
export class Settings {
  readonly apiBase = signal(defaultApiBase());
  readonly apiKey = signal(DEFAULTS.apiKey);
  /** The chain new photos are logged at; null lets each tag's design decide. */
  readonly chain = signal<Chain | null>(DEFAULTS.chain);

  constructor() {
    this.load();
    this.applyLinkConfig();
    // Every change writes straight through, so a reload never loses the config.
    effect(() => {
      const value: Persisted = {
        apiBase: this.apiBase(),
        apiKey: this.apiKey(),
        chain: this.chain(),
      };
      try {
        localStorage.setItem(KEY, JSON.stringify(value));
      } catch {
        // Private browsing. The settings still hold for this session.
      }
    });
  }

  /**
   * Whether this device may change anything. Reading is open to everyone so the
   * log can be shown to people; captures spend a vision call per photo and
   * every mutation is the owner's, so both need the key. Without one the app
   * runs read-only rather than offering buttons that will be refused.
   */
  canWrite(): boolean {
    return this.apiKey().trim().length > 0;
  }

  /** True once the app knows where the API lives. */
  isConfigured(): boolean {
    return this.apiBase().trim().length > 0;
  }

  private load(): void {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return;
      const parsed = { ...DEFAULTS, ...(JSON.parse(raw) as Partial<Persisted>) };
      // An older install may have stored a blank or stale localhost address.
      if (parsed.apiBase) {
        this.apiBase.set(parsed.apiBase);
      }
      this.apiKey.set(parsed.apiKey);
      // Older installs remembered a store id instead, which no longer means anything.
      this.chain.set(CHAINS.includes(parsed.chain as Chain) ? parsed.chain : null);
    } catch {
      try {
        localStorage.removeItem(KEY);
      } catch {
        // Nothing stored to clear.
      }
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
