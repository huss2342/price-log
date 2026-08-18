import { Component, OnInit, inject, signal } from '@angular/core';
import { Api } from '../../core/api';
import type { Observation, Store } from '../../core/models';
import { ObservationCard } from '../../shared/observation-card';

@Component({
  selector: 'app-review',
  imports: [ObservationCard],
  templateUrl: './review.html',
})
export class ReviewPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly pending = signal<Observation[]>([]);
  protected readonly recent = signal<Observation[]>([]);
  protected readonly stores = signal<Store[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.stores().subscribe({ next: (s) => this.stores.set(s), error: () => {} });
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.pending().subscribe({
      next: (rows) => {
        this.pending.set(rows);
        this.loadRecent();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err?.status === 0
            ? 'Cannot reach the API. Check the address in Settings.'
            : (err?.error?.error ?? 'Could not load the review queue.'),
        );
      },
    });
  }

  private loadRecent(): void {
    this.api.recent(40).subscribe({
      next: (rows) => {
        // Anything already in the queue above would just be listed twice.
        const queued = new Set(this.pending().map((p) => p.id));
        this.recent.set(rows.filter((r) => !queued.has(r.id)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected onChanged(updated: Observation): void {
    // A confirmed row leaves the queue and joins the log below it.
    if (!updated.needsReview) {
      this.pending.update((rows) => rows.filter((r) => r.id !== updated.id));
      this.recent.update((rows) => [updated, ...rows.filter((r) => r.id !== updated.id)]);
      return;
    }
    this.pending.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
    this.recent.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
  }

  protected onRemoved(id: number): void {
    this.pending.update((rows) => rows.filter((r) => r.id !== id));
    this.recent.update((rows) => rows.filter((r) => r.id !== id));
  }
}
