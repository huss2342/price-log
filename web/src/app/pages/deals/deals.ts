import { Component, OnInit, computed, inject } from '@angular/core';
import { LogStore } from '../../core/log-store';
import { Settings } from '../../core/settings';
import { dealCard } from '../../shared/cards';
import { Icon } from '../../shared/icon';
import { ItemCard } from '../../shared/item-card';

/**
 * Only what has been bought before and is cheaper right now: sales still running
 * on tags you photographed, and Costco's published offers on the same item
 * numbers. Costco's full listing of massage chairs and gazebos is not here.
 */
@Component({
  selector: 'app-deals',
  imports: [ItemCard, Icon],
  templateUrl: './deals.html',
})
export class DealsPage implements OnInit {
  protected readonly log = inject(LogStore);
  protected readonly settings = inject(Settings);

  protected readonly cards = computed(() => {
    const ctx = { today: this.log.today(), showChain: this.log.multiChain() };
    return this.log.deals().map((deal) => dealCard(deal, ctx));
  });

  /** "Costco checked 3 h ago", so a quiet list is not mistaken for a broken one. */
  protected readonly checked = computed(() => {
    const at = this.log.dealsView()?.lastCheckedAt;
    if (!at) return null;
    const minutes = Math.round((Date.now() - Date.parse(at)) / 60_000);
    if (minutes < 2) return 'just now';
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.round(minutes / 60);
    return hours < 36 ? `${hours} h ago` : `${Math.round(hours / 24)} days ago`;
  });

  ngOnInit(): void {
    void this.log.refresh();
    void this.log.refreshDeals();
  }

  /** Re-reads Costco's page itself. It spends a scrape, so only the owner is offered it. */
  protected check(): void {
    void this.log.refreshDeals(true);
  }
}
