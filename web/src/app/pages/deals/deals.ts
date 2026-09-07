import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import type { DealsView, WatchedItem } from '../../core/models';
import { AgoPipe, MoneyPipe } from '../../shared/format';

@Component({
  selector: 'app-deals',
  imports: [MoneyPipe, AgoPipe, DatePipe, RouterLink],
  templateUrl: './deals.html',
  styleUrl: './deals.scss',
})
export class DealsPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly deals = signal<DealsView | null>(null);
  protected readonly watchlist = signal<WatchedItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(false);
    this.loadWatchlist();
  }

  /** @param force re-read Costco's listing rather than using the cached copy */
  protected load(force: boolean): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.deals(force).subscribe({
      next: (view) => {
        this.deals.set(view);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err?.status === 0
            ? 'Cannot reach the API. Check the address in Settings.'
            : (err?.error?.error ?? 'Could not load deals.'),
        );
      },
    });
  }

  private loadWatchlist(): void {
    this.api.watchlist().subscribe({
      next: (items) => this.watchlist.set(items),
      error: () => this.watchlist.set([]),
    });
  }

  protected unwatch(item: WatchedItem): void {
    this.api.setWatch(item.productId, { watched: false }).subscribe({
      next: () => this.watchlist.update((rows) => rows.filter((r) => r.productId !== item.productId)),
      error: (err) => this.error.set(err?.error?.error ?? 'Could not update the watchlist.'),
    });
  }

  /** Plain-language summary of an item's sale rhythm. */
  protected cycleSummary(item: WatchedItem): string | null {
    const c = item.cycle;
    if (!c.salesSeen) return 'No sale recorded yet.';
    if (!c.averageGapDays) {
      return `Seen on sale once, ${c.daysSinceLastSale} days ago. Needs another to find a pattern.`;
    }

    const basis = c.confident ? '' : ' (only a rough guess so far)';
    if (c.dueInDays !== null && c.dueInDays <= 0) {
      return `Goes on sale about every ${c.averageGapDays} days, and it has been ${c.daysSinceLastSale}. Overdue${basis}.`;
    }
    return `Goes on sale about every ${c.averageGapDays} days. Next one due in roughly ${c.dueInDays} days${basis}.`;
  }
}
