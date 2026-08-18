import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Outbox } from '../../core/outbox';
import { Settings } from '../../core/settings';
import { CHAIN_LABELS, type Observation, type Store } from '../../core/models';
import { ObservationCard } from '../../shared/observation-card';

@Component({
  selector: 'app-capture',
  imports: [RouterLink, ObservationCard],
  templateUrl: './capture.html',
  styleUrl: './capture.scss',
})
export class CapturePage implements OnInit {
  private readonly api = inject(Api);
  protected readonly outbox = inject(Outbox);
  protected readonly settings = inject(Settings);

  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly stores = signal<Store[]>([]);
  protected readonly storeId = signal<number | null>(null);
  protected readonly results = signal<Observation[]>([]);
  protected readonly failures = signal<string[]>([]);
  protected readonly loadError = signal<string | null>(null);

  ngOnInit(): void {
    this.storeId.set(this.settings.defaultStoreId());
    this.loadStores();
    // Anything queued from a previous trip goes up as soon as the app opens.
    void this.drain();
  }

  protected loadStores(): void {
    this.api.stores().subscribe({
      next: (stores) => {
        this.stores.set(stores);
        this.loadError.set(null);
        // A store that no longer exists would silently mislabel captures.
        if (this.storeId() != null && !stores.some((s) => s.id === this.storeId())) {
          this.pickStore(null);
        }
      },
      error: (err) =>
        this.loadError.set(
          err?.status === 0
            ? 'Cannot reach the API. Check the address in Settings.'
            : (err?.error?.error ?? 'Could not load your stores.'),
        ),
    });
  }

  protected pickStore(id: number | null): void {
    this.storeId.set(id);
    // Remembered because the next tag is almost always from the same trip.
    this.settings.defaultStoreId.set(id);
  }

  protected async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    // Clearing lets you pick the same photo twice in a row.
    input.value = '';
    if (!files.length) return;

    for (const file of files) {
      await this.outbox.enqueue(file, file.name || 'tag.jpg', this.storeId());
    }
    await this.drain();
  }

  private async drain(): Promise<void> {
    const { saved, failed } = await this.outbox.flush();
    if (saved.length) {
      this.results.update((current) => [...saved.reverse(), ...current]);
    }
    if (failed.length) {
      this.failures.update((current) => [
        ...failed.map((f) => f.lastError ?? 'Upload failed.'),
        ...current,
      ]);
    }
  }

  protected retryQueue(): void {
    void this.drain();
  }

  protected dismissFailures(): void {
    this.failures.set([]);
  }

  protected onChanged(updated: Observation): void {
    this.results.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
  }

  protected onRemoved(id: number): void {
    this.results.update((rows) => rows.filter((r) => r.id !== id));
  }
}
