import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LogStore } from '../../core/log-store';
import { CHAIN_LABELS, type Store } from '../../core/models';
import { Outbox } from '../../core/outbox';
import { Settings } from '../../core/settings';
import { Stores } from '../../core/stores';
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
  protected readonly stores = inject(Stores);

  protected readonly chainLabels = CHAIN_LABELS;

  /**
   * The store photos are logged at, or null for Auto: no storeId is sent and
   * the API reads the chain off the tag, exactly as before.
   */
  protected readonly selectedStoreId = signal<number | null>(null);
  /** Once the user has chosen, a refresh of the store list must not move it. */
  private storeTouched = false;

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
    void this.stores.load().then(() => {
      if (!this.storeTouched) this.applyDefaultStore();
    });
  }

  protected pickStore(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.storeTouched = true;
    if (value === '') {
      this.selectedStoreId.set(null);
      this.settings.chain.set(null);
      return;
    }
    const id = Number(value);
    this.selectedStoreId.set(id);
    const store = this.stores.stores().find((s) => s.id === id);
    if (store) {
      // The remembered preference follows the store, so the common case stays
      // one tap on the next visit.
      this.settings.chain.set(store.chain);
    }
  }

  protected async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    // Clearing lets you pick the same photo twice in a row.
    input.value = '';
    const storeId = this.selectedStoreId();
    const store = storeId == null ? null : this.stores.stores().find((s) => s.id === storeId);
    // A chosen store sends its id; the chain still rides along for old clients.
    // Auto sends only the chain, exactly as it did before stores existed.
    const chain = store?.chain ?? this.settings.chain();
    for (const file of files) {
      await this.outbox.enqueue(file, file.name || 'tag.jpg', chain, store?.id ?? null);
    }
    await this.outbox.flush();
  }

  protected retry(): void {
    void this.outbox.flush();
  }

  /**
   * The store matching the remembered chain preference, else the first store,
   * else Auto. The preference is moved along with it so the next visit starts
   * in the same place.
   */
  private applyDefaultStore(): void {
    const list = this.stores.stores();
    const preferred = this.settings.chain();
    const match: Store | undefined =
      (preferred ? list.find((s) => s.chain === preferred) : undefined) ?? list[0];
    this.selectedStoreId.set(match?.id ?? null);
    if (match) this.settings.chain.set(match.chain);
  }
}
