import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { CHAIN_LABELS, type Observation, type ObservationUpdate, type Store } from '../core/models';
import { MoneyPipe, UnitPricePipe } from './format';
import { SignalBadge } from './signal-badge';

/**
 * One logged price, with an inline correction form. The same card serves the
 * capture result and the review queue, so a fix is always one tap away from
 * wherever you noticed the mistake.
 */
@Component({
  selector: 'app-observation-card',
  imports: [FormsModule, RouterLink, MoneyPipe, UnitPricePipe, SignalBadge],
  templateUrl: './observation-card.html',
  styleUrl: './observation-card.scss',
})
export class ObservationCard {
  private readonly api = inject(Api);

  readonly observation = input.required<Observation>();
  readonly stores = input<Store[]>([]);
  /** Emitted after a successful save, with the server's updated copy. */
  readonly changed = output<Observation>();
  readonly removed = output<number>();

  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly draft = signal<ObservationUpdate>({});

  /** Price in dollars, because nobody wants to type cents into a phone. */
  protected readonly priceDollars = signal<number | null>(null);

  protected startEdit(): void {
    const o = this.observation();
    this.draft.set({
      productName: o.productName,
      brand: o.brand,
      sizeValue: o.sizeValue,
      sizeUnit: o.sizeUnit,
      packCount: o.packCount,
      storeId: o.storeId,
    });
    this.priceDollars.set(o.priceCents / 100);
    this.error.set(null);
    this.editing.set(true);
  }

  protected cancel(): void {
    this.editing.set(false);
  }

  protected save(confirm: boolean): void {
    const dollars = this.priceDollars();
    const update: ObservationUpdate = {
      ...this.draft(),
      priceCents: dollars == null ? undefined : Math.round(dollars * 100),
      reviewed: confirm,
    };

    this.saving.set(true);
    this.error.set(null);
    this.api.updateObservation(this.observation().id, update).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(false);
        this.changed.emit(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.error ?? 'Could not save that change.');
      },
    });
  }

  /** Accepts the extraction as-is and clears it from the review queue. */
  protected confirmAsIs(): void {
    this.saving.set(true);
    this.api.updateObservation(this.observation().id, { reviewed: true }).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.changed.emit(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.error ?? 'Could not confirm.');
      },
    });
  }

  protected remove(): void {
    if (!globalThis.confirm('Delete this logged price?')) return;
    this.api.deleteObservation(this.observation().id).subscribe({
      next: () => this.removed.emit(this.observation().id),
      error: (err) => this.error.set(err?.error?.error ?? 'Could not delete.'),
    });
  }

  protected photoSrc(key: string): string {
    return this.api.photoUrl(key);
  }

  protected patch<K extends keyof ObservationUpdate>(key: K, value: ObservationUpdate[K]): void {
    this.draft.update((d) => ({ ...d, [key]: value }));
  }
}
