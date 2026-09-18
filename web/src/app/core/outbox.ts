import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import { LogStore } from './log-store';
import type { Chain, Observation } from './models';

const DB_NAME = 'pricelog-outbox';
const STORE = 'captures';

export interface PendingCapture {
  id: number;
  blob: Blob;
  filename: string;
  chain?: Chain | null;
  queuedAt: number;
  attempts: number;
  lastError?: string;
}

/**
 * Captures are queued to IndexedDB before they are uploaded, so a photo taken
 * inside a warehouse with no signal is never lost. The queue drains whenever
 * the browser reports it is back online.
 *
 * <p>Results go straight into the log on the device rather than back to
 * whichever screen started the upload, so a tag read while you are on another
 * tab is still there when you come back.
 */
@Injectable({ providedIn: 'root' })
export class Outbox {
  private readonly api = inject(Api);
  private readonly log = inject(LogStore);
  private db?: IDBDatabase;

  /** Number of captures still waiting to upload. */
  readonly pendingCount = signal(0);
  readonly uploading = signal(false);
  readonly online = signal(navigator.onLine);
  /** Photos the API refused, with its reason, until dismissed. */
  readonly failures = signal<string[]>([]);

  constructor() {
    addEventListener('online', () => {
      this.online.set(true);
      void this.flush();
    });
    addEventListener('offline', () => this.online.set(false));
  }

  async enqueue(blob: Blob, filename: string, chain: Chain | null): Promise<void> {
    const db = await this.open();
    await this.tx(db, 'readwrite', (store) =>
      store.add({ blob, filename, chain, queuedAt: Date.now(), attempts: 0 }),
    );
    await this.refreshCount();
  }

  async list(): Promise<PendingCapture[]> {
    const db = await this.open();
    return this.tx<PendingCapture[]>(db, 'readonly', (store) => store.getAll());
  }

  /**
   * Uploads every queued capture in order. Items that fail with a network error
   * stay queued; items the server rejects outright are dropped, because
   * retrying an unreadable photo forever would block everything behind it.
   *
   * @returns the observations that were successfully created
   */
  async flush(): Promise<Observation[]> {
    if (this.uploading() || !navigator.onLine) {
      return [];
    }
    this.uploading.set(true);
    const saved: Observation[] = [];

    try {
      for (const item of await this.list()) {
        try {
          const file = new File([item.blob], item.filename, { type: item.blob.type });
          const observation = await firstValueFrom(this.api.capture(file, item.chain ?? null));
          saved.push(observation);
          this.log.upsert(observation);
          await this.remove(item.id);
        } catch (error: unknown) {
          const status = (error as { status?: number })?.status ?? 0;
          if (status === 0) {
            // Genuinely offline or the API is unreachable; keep it for later.
            break;
          }
          this.failures.update((current) => [this.message(error), ...current]);
          await this.remove(item.id);
        }
      }
    } finally {
      this.uploading.set(false);
      await this.refreshCount();
    }

    return saved;
  }

  dismissFailures(): void {
    this.failures.set([]);
  }

  async remove(id: number): Promise<void> {
    const db = await this.open();
    await this.tx(db, 'readwrite', (store) => store.delete(id));
    await this.refreshCount();
  }

  async refreshCount(): Promise<void> {
    const db = await this.open();
    this.pendingCount.set(await this.tx<number>(db, 'readonly', (store) => store.count()));
  }

  private message(error: unknown): string {
    const body = (error as { error?: { error?: string } })?.error;
    return body?.error ?? (error as { message?: string })?.message ?? 'Upload failed.';
  }

  private open(): Promise<IDBDatabase> {
    if (this.db) return Promise.resolve(this.db);
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = () => {
        request.result.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true });
      };
      request.onsuccess = () => {
        this.db = request.result;
        resolve(request.result);
      };
      request.onerror = () => reject(request.error);
    });
  }

  private tx<T>(
    db: IDBDatabase,
    mode: IDBTransactionMode,
    action: (store: IDBObjectStore) => IDBRequest,
  ): Promise<T> {
    return new Promise((resolve, reject) => {
      const request = action(db.transaction(STORE, mode).objectStore(STORE));
      request.onsuccess = () => resolve(request.result as T);
      request.onerror = () => reject(request.error);
    });
  }
}
