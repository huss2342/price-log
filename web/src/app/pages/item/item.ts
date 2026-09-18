import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { describeError } from '../../core/errors';
import { LogStore } from '../../core/log-store';
import {
  CATEGORIES,
  CHAIN_LABELS,
  CHAINS,
  categoryLabel,
  type Chain,
  type Observation,
  type ObservationUpdate,
} from '../../core/models';
import { Settings } from '../../core/settings';
import { entryCard } from '../../shared/cards';
import {
  MoneyPipe,
  UnitPricePipe,
  dayLabel,
  money,
  shortDate,
  shortName,
  sizeLabel,
} from '../../shared/format';
import { Icon } from '../../shared/icon';
import { PhotoSrc } from '../../shared/photo-src';
import { rankGroup, wasPrice } from '../../shared/pricing';

interface Draft {
  name: string;
  brand: string;
  commodity: string;
  category: string;
  sizeValue: number | null;
  sizeUnit: string;
  packCount: number | null;
  chain: Chain;
  price: number | null;
  regular: number | null;
  saleEndsOn: string;
  discontinued: boolean;
}

/**
 * One product: what it costs now and normally, how it ranks against what it
 * is compared with, every price logged for it, and the tag itself.
 */
@Component({
  selector: 'app-item',
  imports: [FormsModule, RouterLink, MoneyPipe, UnitPricePipe, Icon, PhotoSrc],
  templateUrl: './item.html',
  styleUrl: './item.scss',
})
export class ItemPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly log = inject(LogStore);
  protected readonly settings = inject(Settings);

  /** Bound from the route: the product id. */
  readonly id = input.required<string>();
  /** Bound from ?entry=: the sighting that was tapped, when it was not the newest. */
  readonly entry = input<string>();

  protected readonly chains = CHAINS;
  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly categories = CATEGORIES;
  protected readonly categoryLabel = categoryLabel;
  protected readonly shortName = shortName;
  protected readonly sizeLabel = sizeLabel;
  protected readonly wasPrice = wasPrice;

  protected readonly product = computed(
    () => this.log.productsById().get(Number(this.id())) ?? null,
  );

  protected readonly selected = computed<Observation | null>(() => {
    const p = this.product();
    if (!p) return null;
    return p.entries.find((e) => e.id === Number(this.entry())) ?? p.latest;
  });

  protected readonly subtitle = computed(() => {
    const p = this.product();
    if (!p) return '';
    const chain = this.log.multiChain() ? CHAIN_LABELS[p.chain] : '';
    return [p.brand ?? '', sizeLabel(p.latest), chain].filter((part) => part).join(' · ');
  });

  protected readonly chips = computed(() => {
    const p = this.product();
    if (!p) return [];
    const card = entryCard(p.latest, { today: this.log.today(), showChain: false });
    // A sale that has ended says nothing about today, which is what this headline is.
    return card.chips.filter((chip) => chip.tone !== 'muted' || p.saleCents != null);
  });

  protected readonly group = computed(() => {
    const p = this.product();
    return p ? this.log.products().filter((o) => o.comparisonKey === p.comparisonKey) : [];
  });
  protected readonly usually = computed(() => rankGroup(this.group(), 'regular'));
  protected readonly today = computed(() => rankGroup(this.group(), 'now'));
  protected readonly unranked = computed(() =>
    this.usually().unranked.map((p) => shortName(p.name)).join(', '),
  );
  /** A second ranking only earns its space when a sale is changing the answer somewhere. */
  protected readonly anySale = computed(() => this.group().some((p) => p.saleCents != null));

  /** Costco's own offer on this item, when its listing has one running. */
  protected readonly listing = computed(() => {
    const p = this.product();
    const today = this.log.today();
    return (
      this.log
        .dealsView()
        ?.matches.find((m) => m.productId === p?.productId && (!m.validUntil || m.validUntil >= today)) ??
      null
    );
  });

  /** What other products in the category are compared as, offered when correcting this one. */
  protected readonly commodities = computed(() => {
    const p = this.product();
    return [...new Set(this.log.products().filter((o) => o.category === p?.category).map((o) => o.commodity))].sort();
  });

  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPhoto = signal(false);
  protected readonly draft = signal<Draft | null>(null);

  protected day(iso: string): string {
    return dayLabel(iso, this.log.today());
  }

  protected date(iso: string | null): string {
    return shortDate(iso, this.log.today());
  }

  protected offer(): string {
    const m = this.listing();
    if (!m) return '';
    const off = m.discountCents ? `${money(m.discountCents, true)} off` : 'On sale';
    const at = m.dealPriceCents != null ? `, ${money(m.dealPriceCents)}` : '';
    const where = m.inWarehouse ? 'in the warehouse' : 'online only';
    const until = m.validUntil ? ` until ${this.date(m.validUntil)}` : '';
    return `${off}${at} ${where}${until}`;
  }

  protected back(): void {
    // Opened straight from a link there is nothing to go back to inside the app.
    if (((history.state as { navigationId?: number })?.navigationId ?? 0) > 1) {
      history.back();
    } else {
      void this.router.navigate(['/browse']);
    }
  }

  protected startEdit(): void {
    const e = this.selected();
    if (!e) return;
    this.draft.set({
      name: e.productName,
      brand: e.brand ?? '',
      commodity: e.commodity,
      category: e.category,
      sizeValue: e.sizeValue,
      sizeUnit: e.sizeUnit ?? '',
      packCount: e.packCount,
      chain: e.chain,
      price: e.priceCents / 100,
      regular: wasPrice(e) == null ? null : wasPrice(e)! / 100,
      saleEndsOn: e.saleEndsOn ?? '',
      discontinued: e.discontinued,
    });
    this.error.set(null);
    this.editing.set(true);
  }

  protected patch<K extends keyof Draft>(key: K, value: Draft[K]): void {
    this.draft.update((d) => (d ? { ...d, [key]: value } : d));
  }

  protected save(): void {
    const e = this.selected();
    const d = this.draft();
    if (!e || !d || d.price == null) return;

    const priceCents = Math.round(d.price * 100);
    const regularCents = d.regular == null ? null : Math.round(d.regular * 100);
    const update: ObservationUpdate = {
      productName: d.name.trim() || undefined,
      brand: d.brand.trim(),
      commodity: d.commodity.trim() || undefined,
      category: d.category,
      sizeValue: d.sizeValue,
      sizeUnit: d.sizeUnit.trim(),
      packCount: d.packCount,
      chain: d.chain,
      priceCents,
      reviewed: true,
    };
    if (regularCents != null && regularCents > priceCents) {
      update.regularPriceCents = regularCents;
      update.saleEndsOn = d.saleEndsOn || undefined;
    } else if (wasPrice(e) != null) {
      // The regular price was cleared, so this was not a sale after all.
      update.onSale = false;
    }
    if (d.discontinued !== e.discontinued) {
      update.discontinued = d.discontinued;
    }
    this.send(e.id, update, () => this.editing.set(false));
  }

  /** Accepts the reading as it is. */
  protected confirm(): void {
    const e = this.selected();
    if (e) this.send(e.id, { reviewed: true });
  }

  protected remove(): void {
    const e = this.selected();
    const p = this.product();
    if (!e || !p || !globalThis.confirm('Delete this entry and its photo?')) return;

    this.api.deleteObservation(e.id).subscribe({
      next: () => {
        this.log.remove(e.id);
        if (p.entries.length === 1) {
          void this.router.navigate(['/browse'], { replaceUrl: true });
        } else {
          void this.router.navigate([], { queryParams: {}, replaceUrl: true });
        }
      },
      error: (err) => this.error.set(describeError(err, 'Could not delete that entry.')),
    });
  }

  private send(id: number, update: ObservationUpdate, done?: () => void): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.updateObservation(id, update).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.log.upsert(saved);
        done?.();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(describeError(err, 'Could not save that change.'));
      },
    });
  }
}
