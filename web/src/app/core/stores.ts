import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { isDemoMode } from '../demo/demo-mode';
import { Api } from './api';
import { describeError } from './errors';
import { CHAINS, type Chain, type Store } from './models';
import { Settings } from './settings';

export interface NewStore {
  chain: Chain;
  label: string;
  city?: string;
  state?: string;
}

export interface StorePatch {
  label?: string;
  city?: string | null;
  state?: string | null;
}

/**
 * The user's named stores: "Costco — Manchester" rather than just "Costco".
 *
 * <p>Every screen reads from here rather than asking the API. The stores page
 * manages them; the capture page picks one per photo. In demo mode there are
 * no stores, and the picker falls back to the chain alone.
 */
@Injectable({ providedIn: 'root' })
export class Stores {
  private readonly api = inject(Api);
  private readonly settings = inject(Settings);

  readonly stores = signal<Store[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Loads the stores unless there is no API to ask, as in demo mode. */
  async load(): Promise<void> {
    if (isDemoMode() || !this.settings.isConfigured()) return;
    this.loading.set(true);
    try {
      this.stores.set(await firstValueFrom(this.api.stores()));
      this.error.set(null);
    } catch (err) {
      this.error.set(describeError(err, 'Could not load your stores.'));
    } finally {
      this.loading.set(false);
    }
  }

  async create(data: NewStore): Promise<Store> {
    const store = await firstValueFrom(this.api.createStore(data));
    this.stores.update((rows) => [...rows, store].sort(byChainThenLabel));
    return store;
  }

  async rename(id: number, data: StorePatch): Promise<Store> {
    const saved = await firstValueFrom(this.api.updateStore(id, data));
    this.stores.update((rows) =>
      rows.map((row) => (row.id === saved.id ? saved : row)).sort(byChainThenLabel),
    );
    return saved;
  }

  async remove(id: number): Promise<void> {
    await firstValueFrom(this.api.deleteStore(id));
    this.stores.update((rows) => rows.filter((row) => row.id !== id));
  }
}

function byChainThenLabel(a: Store, b: Store): number {
  const order = (chain: Chain) => CHAINS.indexOf(chain);
  return order(a.chain) - order(b.chain) || a.label.localeCompare(b.label);
}
