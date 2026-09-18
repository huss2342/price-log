import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { isDemoMode } from '../demo/demo-mode';
import { currentDeals, isoToday, summarize } from '../shared/pricing';
import { Api } from './api';
import { describeError } from './errors';
import type { DealsView, Observation } from './models';
import { Settings } from './settings';

const CACHE_KEY = 'pricelog.log';

/** Opening a screen re-reads the log at most this often. Captures and edits update it directly. */
const LOG_FRESH_MS = 60_000;

/** Costco's listing changes daily at most, and the API caches it for a day besides. */
const DEALS_FRESH_MS = 10 * 60_000;

/** Fields that belong to the product, so an edit to one sighting changes every sighting. */
const PRODUCT_FIELDS = [
  'productName',
  'brand',
  'category',
  'comparisonKey',
  'commodity',
  'attributes',
  'sizeValue',
  'sizeUnit',
  'packCount',
  'baseUnit',
  'baseQuantity',
] as const satisfies ReadonlyArray<keyof Observation>;

interface Cached {
  /** The log belongs to one API; a device pointed somewhere else starts over. */
  apiBase: string;
  entries: Observation[];
  deals: DealsView | null;
}

/**
 * The log, kept on the device.
 *
 * <p>Every screen reads from here rather than asking the API. The API scales to
 * zero, and waiting half a minute for it to wake before a list of your own
 * purchases could appear was the slowest thing in the app. The copy on the
 * device shows immediately and is refreshed behind it; a capture or an edit
 * lands in it straight away, so nothing vanishes when you change tabs.
 */
@Injectable({ providedIn: 'root' })
export class LogStore {
  private readonly api = inject(Api);
  private readonly settings = inject(Settings);

  /** The sample log is never written over the real one. */
  private readonly persist = !isDemoMode();

  readonly entries = signal<Observation[]>([]);
  readonly dealsView = signal<DealsView | null>(null);
  /** True once there is something to show, from the device or the network. */
  readonly loaded = signal(false);
  readonly syncing = signal(false);
  readonly syncError = signal<string | null>(null);
  readonly checkingDeals = signal(false);
  readonly dealsError = signal<string | null>(null);

  /** Moves on at the next refresh after midnight, so sales end on the right day. */
  readonly today = signal(isoToday());

  readonly products = computed(() => summarize(this.entries(), this.today()));
  readonly productsById = computed(() => new Map(this.products().map((p) => [p.productId, p])));
  readonly deals = computed(() =>
    currentDeals(this.products(), this.dealsView()?.matches ?? [], this.today()),
  );
  /** Naming the chain on every card is only worth it once there is more than one. */
  readonly multiChain = computed(() => new Set(this.entries().map((e) => e.chain)).size > 1);

  private syncedAt = 0;
  private dealsAt = 0;
  private logRequest: Promise<void> | null = null;
  private dealsRequest: Promise<void> | null = null;

  constructor() {
    this.restore();
  }

  /** Re-reads the log unless it was read moments ago. */
  refresh(force = false): Promise<void> {
    this.today.set(isoToday());
    if (this.logRequest) return this.logRequest;
    if (!force && Date.now() - this.syncedAt < LOG_FRESH_MS) return Promise.resolve();
    if (this.persist && !this.settings.isConfigured()) {
      this.loaded.set(true);
      return Promise.resolve();
    }

    this.syncing.set(true);
    this.logRequest = firstValueFrom(this.api.observations())
      .then((rows) => {
        this.entries.set(rows);
        this.syncedAt = Date.now();
        this.syncError.set(null);
        this.save();
      })
      .catch((err) => this.syncError.set(describeError(err, 'Could not update the log.')))
      .finally(() => {
        this.syncing.set(false);
        this.loaded.set(true);
        this.logRequest = null;
      });
    return this.logRequest;
  }

  /** @param force re-read Costco's page too, rather than the API's cached copy */
  refreshDeals(force = false): Promise<void> {
    if (this.dealsRequest) return this.dealsRequest;
    if (!force && Date.now() - this.dealsAt < DEALS_FRESH_MS) return Promise.resolve();
    if (this.persist && !this.settings.isConfigured()) return Promise.resolve();

    this.checkingDeals.set(true);
    this.dealsRequest = firstValueFrom(this.api.deals(force))
      .then((view) => {
        this.dealsView.set(view);
        this.dealsAt = Date.now();
        this.dealsError.set(null);
        this.save();
      })
      .catch((err) => this.dealsError.set(describeError(err, 'Could not check for deals.')))
      .finally(() => {
        this.checkingDeals.set(false);
        this.dealsRequest = null;
      });
    return this.dealsRequest;
  }

  /** Adds or replaces one sighting, carrying product-level changes to its siblings. */
  upsert(entry: Observation): void {
    this.entries.update((rows) => {
      const product = Object.fromEntries(PRODUCT_FIELDS.map((f) => [f, entry[f]]));
      const others = rows
        .filter((row) => row.id !== entry.id)
        .map((row) => (row.productId === entry.productId ? { ...row, ...product } : row));
      return [entry, ...others];
    });
    this.loaded.set(true);
    this.save();
  }

  remove(id: number): void {
    this.entries.update((rows) => rows.filter((row) => row.id !== id));
    this.save();
  }

  private restore(): void {
    if (!this.persist) return;
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      if (!raw) return;
      const cached = JSON.parse(raw) as Cached;
      if (cached.apiBase !== this.settings.apiBase()) return;
      this.entries.set(cached.entries ?? []);
      this.dealsView.set(cached.deals ?? null);
      this.loaded.set(true);
    } catch {
      // A corrupt or unreadable copy is simply fetched again.
    }
  }

  private save(): void {
    if (!this.persist) return;
    const cached: Cached = {
      apiBase: this.settings.apiBase(),
      entries: this.entries(),
      deals: this.dealsView(),
    };
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(cached));
    } catch {
      // Out of room or private browsing: it still works, just from the network.
    }
  }
}
