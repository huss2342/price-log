import type { BaseUnit, Chain, DealMatch, Observation } from '../core/models';

/**
 * The price arithmetic every screen shares, kept free of Angular so that the
 * list, the comparison and the deals all agree on what "on sale" means.
 */

const DAY_MS = 86_400_000;

/** A sale with no end date printed is taken to run about as long as Costco's do. */
const UNDATED_SALE_DAYS = 28;

/** Today as YYYY-MM-DD in local time, the same shape the API sends dates in. */
export function isoToday(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}

export function newestFirst(a: Observation, b: Observation): number {
  return b.observedOn.localeCompare(a.observedOn) || b.id - a.id;
}

/** The price printed beside a sale price, when it really is higher. */
export function wasPrice(o: Observation): number | null {
  return o.regularPriceCents != null && o.regularPriceCents > o.priceCents
    ? o.regularPriceCents
    : null;
}

export function isSale(o: Observation): boolean {
  return wasPrice(o) != null || o.onSale;
}

/** Whether this sighting's sale is still on, going by the date printed on the tag. */
export function saleRunning(o: Observation, today: string): boolean {
  if (!isSale(o)) return false;
  if (o.saleEndsOn) return o.saleEndsOn >= today;
  return daysBetween(o.observedOn, today) <= UNDATED_SALE_DAYS;
}

/** Cents per base unit, or null when the size is unknown. */
export function perUnit(
  cents: number | null,
  baseUnit: BaseUnit,
  baseQuantity: number | null,
): number | null {
  if (cents == null || baseUnit === 'NONE' || !baseQuantity) return null;
  return cents / baseQuantity;
}

/**
 * What the item costs without a promotion: the regular price printed beside a
 * sale, or failing that the newest sighting that was not on sale.
 *
 * @param entries one product's sightings, newest first
 */
export function regularOf(entries: Observation[]): number | null {
  for (const entry of entries) {
    const was = wasPrice(entry);
    if (was != null) return was;
    if (!entry.onSale) return entry.priceCents;
  }
  return null;
}

/** One product as it stands today, built from every time it was photographed. */
export interface ProductSummary {
  productId: number;
  comparisonKey: string;
  commodity: string;
  category: string;
  name: string;
  brand: string | null;
  chain: Chain;
  baseUnit: BaseUnit;
  baseQuantity: number | null;
  latest: Observation;
  /** Newest first. */
  entries: Observation[];
  regularCents: number | null;
  /** The sale price while a sale is running, otherwise null. */
  saleCents: number | null;
  saleEndsOn: string | null;
  /** What it rings up at today. */
  nowCents: number;
  regularUnit: number | null;
  nowUnit: number | null;
  discontinued: boolean;
  needsReview: boolean;
}

export function summarize(entries: Observation[], today: string): ProductSummary[] {
  const byProduct = new Map<number, Observation[]>();
  for (const entry of [...entries].sort(newestFirst)) {
    const list = byProduct.get(entry.productId);
    if (list) {
      list.push(entry);
    } else {
      byProduct.set(entry.productId, [entry]);
    }
  }

  return [...byProduct.values()].map((list) => {
    const latest = list[0];
    const regularCents = regularOf(list);
    const saleCents = saleRunning(latest, today) ? latest.priceCents : null;
    const nowCents = saleCents ?? regularCents ?? latest.priceCents;
    return {
      productId: latest.productId,
      comparisonKey: latest.comparisonKey,
      commodity: latest.commodity,
      category: latest.category,
      name: latest.productName,
      brand: latest.brand,
      chain: latest.chain,
      baseUnit: latest.baseUnit,
      baseQuantity: latest.baseQuantity,
      latest,
      entries: list,
      regularCents,
      saleCents,
      saleEndsOn: saleCents != null ? latest.saleEndsOn : null,
      nowCents,
      regularUnit: perUnit(regularCents, latest.baseUnit, latest.baseQuantity),
      nowUnit: perUnit(nowCents, latest.baseUnit, latest.baseQuantity),
      discontinued: latest.discontinued,
      needsReview: latest.needsReview,
    };
  });
}

export interface Ranked {
  product: ProductSummary;
  priceCents: number;
  unitCents: number;
  onSale: boolean;
  /** How much more per unit than the cheapest, rounded; 0 for the cheapest. */
  percentAbove: number;
}

/**
 * Ranks one comparison group cheapest first, by price per unit: either as the
 * items normally cost, or as they ring up today with whatever sale is running.
 * Products sized in a different unit, or not sized at all, cannot be ranked
 * fairly and are returned apart.
 */
export function rankGroup(
  products: ProductSummary[],
  basis: 'regular' | 'now',
): { ranked: Ranked[]; unranked: ProductSummary[] } {
  const unit = commonUnit(products);
  const ranked: Ranked[] = [];
  const unranked: ProductSummary[] = [];

  for (const product of products) {
    const priceCents = basis === 'regular' ? product.regularCents : product.nowCents;
    const unitCents = basis === 'regular' ? product.regularUnit : product.nowUnit;
    if (priceCents == null || unitCents == null || product.baseUnit !== unit) {
      unranked.push(product);
      continue;
    }
    ranked.push({
      product,
      priceCents,
      unitCents,
      onSale: basis === 'now' && product.saleCents != null,
      percentAbove: 0,
    });
  }

  ranked.sort((a, b) => a.unitCents - b.unitCents);
  const best = ranked[0]?.unitCents;
  for (const row of ranked) {
    row.percentAbove = best ? Math.round(((row.unitCents - best) / best) * 100) : 0;
  }
  return { ranked, unranked };
}

/** The unit most of a group is sized in, so a stray count-based tag cannot skew an ounce ranking. */
function commonUnit(products: ProductSummary[]): BaseUnit {
  const counts = new Map<BaseUnit, number>();
  for (const p of products) {
    if (p.baseUnit !== 'NONE' && p.baseQuantity) {
      counts.set(p.baseUnit, (counts.get(p.baseUnit) ?? 0) + 1);
    }
  }
  let best: BaseUnit = 'NONE';
  let bestCount = 0;
  for (const [unit, count] of counts) {
    if (count > bestCount) {
      best = unit;
      bestCount = count;
    }
  }
  return best;
}

/** Something bought before that is cheaper right now. */
export interface DealEntry {
  product: ProductSummary;
  regularCents: number | null;
  dealCents: number | null;
  offCents: number | null;
  endsOn: string | null;
  /** On sale on a tag you photographed. */
  seenInStore: boolean;
  /** Where Costco's own listing says the offer applies, if it lists one. */
  listed: 'warehouse' | 'online' | null;
  /** True when a figure was worked out rather than printed. */
  estimated: boolean;
}

/**
 * Everything already bought that is on sale now: running sales on your own
 * tags, and Costco's published offers on the same item numbers. One entry per
 * product. Costco's figures win where both exist, since they are its own.
 */
export function currentDeals(
  products: ProductSummary[],
  matches: DealMatch[],
  today: string,
): DealEntry[] {
  const byId = new Map(products.map((p) => [p.productId, p]));
  const deals = new Map<number, DealEntry>();

  for (const match of matches) {
    const product = byId.get(match.productId);
    if (!product || deals.has(match.productId)) continue;
    if (match.validUntil && match.validUntil < today) continue;
    deals.set(match.productId, {
      product,
      regularCents: match.regularPriceCents ?? product.regularCents,
      dealCents: match.dealPriceCents,
      offCents: match.discountCents,
      endsOn: match.validUntil,
      seenInStore: false,
      listed: match.inWarehouse ? 'warehouse' : 'online',
      estimated: match.estimated,
    });
  }

  for (const product of products) {
    if (product.saleCents == null) continue;
    const listed = deals.get(product.productId);
    if (listed) {
      listed.seenInStore = true;
      listed.endsOn ??= product.saleEndsOn;
      continue;
    }
    const regular = product.regularCents;
    deals.set(product.productId, {
      product,
      regularCents: regular,
      dealCents: product.saleCents,
      offCents: regular != null && regular > product.saleCents ? regular - product.saleCents : null,
      endsOn: product.saleEndsOn,
      seenInStore: true,
      listed: null,
      estimated: false,
    });
  }

  return [...deals.values()].sort(
    (a, b) =>
      (a.endsOn ?? '9999').localeCompare(b.endsOn ?? '9999') ||
      (b.offCents ?? 0) - (a.offCents ?? 0),
  );
}
