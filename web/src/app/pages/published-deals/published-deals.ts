import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import type { BrowsedDeal } from '../../core/models';
import { MoneyPipe } from '../../shared/format';

/**
 * The full Costco listing. The deals screen only shows promotions on items
 * already photographed, which is a handful out of a couple of hundred; the rest
 * were already stored and simply had nowhere to be read.
 */
@Component({
  selector: 'app-published-deals',
  imports: [FormsModule, RouterLink, MoneyPipe],
  templateUrl: './published-deals.html',
  styleUrl: './published-deals.scss',
})
export class PublishedDealsPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly deals = signal<BrowsedDeal[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly warehouseOnly = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.publishedDeals(this.query(), this.warehouseOnly()).subscribe({
      next: (rows) => {
        this.deals.set(rows);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err?.status === 0
            ? 'Cannot reach the API. Check the address in Settings.'
            : (err?.error?.error ?? 'Could not load the listing.'),
        );
      },
    });
  }

  protected toggleWarehouse(): void {
    this.warehouseOnly.update((on) => !on);
    this.load();
  }

  protected clear(): void {
    this.query.set('');
    this.load();
  }
}
