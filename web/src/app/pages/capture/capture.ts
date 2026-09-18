import { Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LogStore } from '../../core/log-store';
import { CHAIN_LABELS, CHAINS, type Chain } from '../../core/models';
import { Outbox } from '../../core/outbox';
import { Settings } from '../../core/settings';
import { entryCard } from '../../shared/cards';
import { Icon } from '../../shared/icon';
import { ItemCard } from '../../shared/item-card';

/** How many of the latest captures sit under the shutter. The rest are in Entries. */
const RECENT = 12;

@Component({
  selector: 'app-capture',
  imports: [RouterLink, ItemCard, Icon],
  templateUrl: './capture.html',
  styleUrl: './capture.scss',
})
export class CapturePage implements OnInit {
  private readonly log = inject(LogStore);
  protected readonly outbox = inject(Outbox);
  protected readonly settings = inject(Settings);

  protected readonly chains = CHAINS.filter((chain) => chain !== 'OTHER');
  protected readonly chainLabels = CHAIN_LABELS;

  /** Newest first by when they were logged, not by the date printed on the tag. */
  protected readonly recent = computed(() => {
    const ctx = { today: this.log.today(), showChain: this.log.multiChain() };
    return [...this.log.entries()]
      .sort((a, b) => b.id - a.id)
      .slice(0, RECENT)
      .map((entry) => entryCard(entry, ctx));
  });

  ngOnInit(): void {
    // Anything queued from a previous trip goes up as soon as the screen opens.
    void this.outbox.refreshCount();
    void this.outbox.flush();
  }

  protected pick(chain: Chain | null): void {
    this.settings.chain.set(chain);
  }

  protected async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    // Clearing lets you pick the same photo twice in a row.
    input.value = '';
    for (const file of files) {
      await this.outbox.enqueue(file, file.name || 'tag.jpg', this.settings.chain());
    }
    await this.outbox.flush();
  }

  protected retry(): void {
    void this.outbox.flush();
  }
}
