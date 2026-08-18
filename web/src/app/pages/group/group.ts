import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Api } from '../../core/api';
import { CHAIN_LABELS, type CompareGroup, type StoreOffer } from '../../core/models';
import { GroupCard } from '../../shared/group-card';
import { AgoPipe, MoneyPipe, UnitPricePipe } from '../../shared/format';
import { SignalBadge } from '../../shared/signal-badge';

@Component({
  selector: 'app-group',
  imports: [GroupCard, MoneyPipe, UnitPricePipe, AgoPipe, SignalBadge],
  templateUrl: './group.html',
  styleUrl: './group.scss',
})
export class GroupPage implements OnInit {
  private readonly api = inject(Api);
  private readonly location = inject(Location);

  /** Bound from the :key route parameter. */
  readonly key = input.required<string>();

  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly group = signal<CompareGroup | null>(null);
  protected readonly history = signal<StoreOffer[]>([]);
  protected readonly openProductId = signal<number | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.group(this.key()).subscribe({
      next: (group) => {
        this.group.set(group);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error ?? 'Could not load that comparison.');
      },
    });
  }

  /** Price history is fetched per product, only when you ask to see it. */
  protected toggleHistory(productId: number): void {
    if (this.openProductId() === productId) {
      this.openProductId.set(null);
      return;
    }
    this.openProductId.set(productId);
    this.history.set([]);
    this.api.history(productId).subscribe({
      next: (rows) => this.history.set(rows),
      error: () => this.history.set([]),
    });
  }

  protected back(): void {
    this.location.back();
  }
}
