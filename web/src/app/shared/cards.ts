import { CHAIN_LABELS, type Chain, type Observation } from '../core/models';
import { dayLabel, money, shortDate, shortName, sizeLabel, unitPrice } from './format';
import {
  isSale,
  perUnit,
  saleRunning,
  wasPrice,
  type DealEntry,
  type ProductSummary,
} from './pricing';

export type Tone = 'sale' | 'warn' | 'good' | 'info' | 'muted';

export interface Chip {
  text: string;
  tone: Tone;
}

/**
 * Everything a card shows. Every list in the app renders this one shape, so an
 * item looks the same in Snap, Browse, Deals and Entries. Only what is worth
 * reading at a glance goes on it; the rest is one tap away.
 */
export interface CardView {
  productId: number;
  /** The sighting to open, or null for the product's newest. */
  entryId: number | null;
  name: string;
  /** "40 oz · 25.0¢/oz · Today" */
  detail: string;
  priceCents: number;
  /** The regular price, struck through beside a sale price. */
  wasCents: number | null;
  chips: Chip[];
}

export interface CardContext {
  today: string;
  /** Name the chain only when the log spans more than one. */
  showChain: boolean;
}

const NOT_RESTOCKING: Chip = { text: 'Not restocking', tone: 'warn' };
const NEEDS_REVIEW: Chip = { text: 'Check reading', tone: 'info' };

/** One sighting, as the snap list and the entries log show it. */
export function entryCard(e: Observation, ctx: CardContext): CardView {
  const was = wasPrice(e);
  const chips: Chip[] = [];
  if (isSale(e)) {
    chips.push(saleChip(was == null ? null : was - e.priceCents, e.saleEndsOn, saleRunning(e, ctx.today), ctx.today));
  }
  if (e.discontinued) chips.push(NOT_RESTOCKING);
  if (e.needsReview) chips.push(NEEDS_REVIEW);

  return {
    productId: e.productId,
    entryId: e.id,
    name: shortName(e.productName),
    detail: join(
      sizeLabel(e),
      unitPrice(perUnit(e.priceCents, e.baseUnit, e.baseQuantity), e.baseUnit),
      chainOf(e.chain, ctx),
      dayLabel(e.observedOn, ctx.today),
    ),
    priceCents: e.priceCents,
    wasCents: was,
    chips,
  };
}

/** A product as it stands today, which is what browsing and comparing care about. */
export function productCard(p: ProductSummary, ctx: CardContext, lead: Chip[] = []): CardView {
  const chips = [...lead];
  const was = p.saleCents != null && p.regularCents != null && p.regularCents > p.saleCents ? p.regularCents : null;
  if (p.saleCents != null) {
    chips.push(saleChip(was == null ? null : was - p.saleCents, p.saleEndsOn, true, ctx.today));
  }
  if (p.discontinued) chips.push(NOT_RESTOCKING);
  if (p.needsReview) chips.push(NEEDS_REVIEW);

  return {
    productId: p.productId,
    entryId: null,
    name: shortName(p.name),
    detail: join(
      sizeLabel(p.latest),
      unitPrice(p.nowUnit, p.baseUnit),
      chainOf(p.chain, ctx),
      dayLabel(p.latest.observedOn, ctx.today),
    ),
    priceCents: p.nowCents,
    wasCents: was,
    chips,
  };
}

/** Before and after, how much, and until when: the three things a deal is read for. */
export function dealCard(d: DealEntry, ctx: CardContext): CardView {
  const p = d.product;
  const price = d.dealCents ?? p.nowCents;
  const was = d.regularCents != null && d.regularCents > price ? d.regularCents : null;
  const chips = [saleChip(d.offCents ?? (was == null ? null : was - price), d.endsOn, true, ctx.today)];
  if (d.listed === 'online') chips.push({ text: 'Online only', tone: 'muted' });
  if (p.discontinued) chips.push(NOT_RESTOCKING);

  return {
    productId: p.productId,
    entryId: null,
    name: shortName(p.name),
    detail: join(
      sizeLabel(p.latest),
      unitPrice(perUnit(price, p.baseUnit, p.baseQuantity), p.baseUnit),
      chainOf(p.chain, ctx),
    ),
    priceCents: price,
    wasCents: was,
    chips,
  };
}

function saleChip(offCents: number | null, endsOn: string | null, running: boolean, today: string): Chip {
  const off = offCents != null && offCents > 0 ? `${money(offCents, true)} off` : 'Sale';
  if (!endsOn) {
    return { text: off, tone: running ? 'sale' : 'muted' };
  }
  const date = shortDate(endsOn, today);
  return running
    ? { text: `${off} · till ${date}`, tone: 'sale' }
    : { text: `${off} · ended ${date}`, tone: 'muted' };
}

function chainOf(chain: Chain, ctx: CardContext): string {
  return ctx.showChain ? CHAIN_LABELS[chain] : '';
}

function join(...parts: string[]): string {
  return parts.filter(Boolean).join(' · ');
}
