import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import type { Observation } from './models';

const DB_NAME = 'pricelog-outbox';
const STORE = 'captures';

export interface PendingCapture {
  id: number;
  blob: Blob;
  filename: string;
  storeId: number | null;
  queuedAt: number;
  attempts: number;
  lastError?: string;
}

/**
 * Captures are queued to IndexedDB before they are uploaded, so a photo taken
 * inside a warehouse with no signal is never lost. The queue drains whenever
 * the browser reports it is back online.
 */
@Injectable({ providedIn: 'root' })
export class Outbox {
  private readonly api = inject(Api);
  private db?: IDBDatabase;

  /** Number of captures still waiting to upload. */
  readonly pendingCount = signal(0);
  readonly uploading = signal(false);
  readonly online = signal(navigator.onLine);

  constructor() {
    addEventListener('online', () => {
      this.online.set(true);
      void this.flush();
    });
    addEventListener('offline', () => this.online.set(false));
  }

  async enqueue(blob: Blob, filename: string, storeId: number | null): Promise<void> {
    const db = await this.open();
    await this.tx(db, 'readwrite', (store) =>
      store.add({ blob, filename, storeId, queuedAt: Date.now(), attempts: 0 }),
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
  async flush(): Promise<{ saved: Observation[]; failed: PendingCapture[] }> {
    if (this.uploading() || !navigator.onLine) {
      return { saved: [], failed: [] };
    }
    this.uploading.set(true);
    const saved: Observation[] = [];
    const failed: PendingCapture[] = [];

    try {
      for (const item of await this.list()) {
        try {
          const file = new File([item.blob], item.filename, { type: item.blob.type });
          saved.push(await firstValueFrom(this.api.capture(file, item.storeId)));
          await this.remove(item.id);
        } catch (error: unknown) {
          const status = (error as { status?: number })?.status ?? 0;
          if (status === 0) {
            // Genuinely offline or the API is unreachable; keep it for later.
            break;
          }
          failed.push({ ...item, lastError: this.message(error) });
          await this.remove(item.id);
        }
      }
    } finally {
      this.uploading.set(false);
      await this.refreshCount();
    }

    return { saved, failed };
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
