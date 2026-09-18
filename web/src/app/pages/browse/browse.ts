import { Component, computed, inject, signal } from '@angular/core';
import { LogStore } from '../../core/log-store';
import { CATEGORIES, categoryLabel } from '../../core/models';
import { productCard, type CardContext, type CardView, type Chip } from '../../shared/cards';
import { Icon } from '../../shared/icon';
import { ItemCard } from '../../shared/item-card';
import { rankGroup, type ProductSummary } from '../../shared/pricing';

interface Group {
  key: string;
  /** Only set when there is more than one product to compare. */
  title: string | null;
  cards: CardView[];
}

interface Section {
  category: string;
  label: string;
  groups: Group[];
}

/**
 * Everything bought, straight from the log on the device: no search needed to
 * see it and no request to wait on. Products that compare with each other sit
 * together, cheapest first.
 */
@Component({
  selector: 'app-browse',
  imports: [ItemCard, Icon],
  templateUrl: './browse.html',
  styleUrl: './browse.scss',
})
export class BrowsePage {
  protected readonly log = inject(LogStore);

  protected readonly query = signal('');
  protected readonly category = signal<string | null>(null);

  /** Only categories something has actually been logged in. */
  protected readonly categories = computed(() => {
    const counts = new Map<string, number>();
    for (const p of this.log.products()) {
      counts.set(p.category, (counts.get(p.category) ?? 0) + 1);
    }
    return orderCategories([...counts.keys()]).map((category) => ({
      category,
      label: categoryLabel(category),
      count: counts.get(category) ?? 0,
    }));
  });

  protected readonly sections = computed<Section[]>(() => {
    const needle = this.query().trim().toLowerCase();
    const picked = this.category();
    const ctx: CardContext = { today: this.log.today(), showChain: this.log.multiChain() };

    const byCategory = new Map<string, Map<string, ProductSummary[]>>();
    for (const p of this.log.products()) {
      if (picked && p.category !== picked) continue;
      if (needle && !matches(p, needle)) continue;
      const groups = byCategory.get(p.category) ?? new Map<string, ProductSummary[]>();
      groups.set(p.comparisonKey, [...(groups.get(p.comparisonKey) ?? []), p]);
      byCategory.set(p.category, groups);
    }

    return orderCategories([...byCategory.keys()]).map((category) => ({
      category,
      label: categoryLabel(category),
      groups: [...(byCategory.get(category)?.values() ?? [])]
        .map((products) => toGroup(products, ctx))
        .sort((a, b) => (a.title ?? a.cards[0].name).localeCompare(b.title ?? b.cards[0].name)),
    }));
  });

  protected toggle(category: string): void {
    this.category.update((current) => (current === category ? null : category));
  }

  protected search(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}

function matches(p: ProductSummary, needle: string): boolean {
  return [p.name, p.brand ?? '', p.commodity].some((text) => text.toLowerCase().includes(needle));
}

function orderCategories(categories: string[]): string[] {
  const known = CATEGORIES as readonly string[];
  const rank = (c: string) => (known.includes(c) ? known.indexOf(c) : known.length);
  return categories.sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

/**
 * Ranks a comparison by what it costs today. When a sale changes the answer,
 * both winners are marked: the cheapest now, and the cheapest usually.
 */
function toGroup(products: ProductSummary[], ctx: CardContext): Group {
  const key = products[0].comparisonKey;
  if (products.length === 1) {
    return { key, title: null, cards: [productCard(products[0], ctx)] };
  }

  const now = rankGroup(products, 'now');
  const regular = rankGroup(products, 'regular');
  const cheapestNow = now.ranked.length > 1 ? now.ranked[0].product.productId : null;
  const cheapestUsually = regular.ranked.length > 1 ? regular.ranked[0].product.productId : null;

  const badges = (p: ProductSummary): Chip[] => {
    if (p.productId === cheapestNow && cheapestNow === cheapestUsually) {
      return [{ text: 'Cheapest', tone: 'good' }];
    }
    if (p.productId === cheapestNow) return [{ text: 'Cheapest now', tone: 'good' }];
    if (p.productId === cheapestUsually) return [{ text: 'Cheapest usually', tone: 'good' }];
    return [];
  };

  const commodity = products[0].commodity;
  return {
    key,
    title: commodity.charAt(0).toUpperCase() + commodity.slice(1),
    cards: [...now.ranked.map((r) => r.product), ...now.unranked].map((p) =>
      productCard(p, ctx, badges(p)),
    ),
  };
}
