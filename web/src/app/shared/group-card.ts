import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CHAIN_LABELS, type CompareGroup } from '../core/models';
import { AgoPipe, MoneyPipe, UnitPricePipe } from './format';
import { SignalBadge } from './signal-badge';

/**
 * One comparison group: substitutable products ranked by normalized unit price,
 * so the cheapest store is the first thing you read.
 */
@Component({
  selector: 'app-group-card',
  imports: [RouterLink, MoneyPipe, UnitPricePipe, AgoPipe, SignalBadge],
  templateUrl: './group-card.html',
  styleUrl: './group-card.scss',
})
export class GroupCard {
  readonly group = input.required<CompareGroup>();
  /** Collapses to the best offer plus a count when many stores are logged. */
  readonly compact = input(false);

  protected readonly chainLabels = CHAIN_LABELS;

  protected readonly visibleOffers = computed(() => {
    const offers = this.group().offers;
    return this.compact() ? offers.slice(0, 3) : offers;
  });

  protected readonly hiddenCount = computed(
    () => this.group().offers.length - this.visibleOffers().length,
  );

  protected readonly comparable = computed(() => this.group().baseUnit !== 'NONE');
}
