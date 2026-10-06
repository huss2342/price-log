import { currentDeals, rankGroup, summarize } from './pricing';
import type { DealMatch, Observation } from '../core/models';

function sighting(overrides: Partial<Observation> = {}): Observation {
  return {
    id: 1,
    productId: 10,
    productName: 'Kirkland Chicken Sausage',
    brand: 'Kirkland',
    category: 'MEAT',
    comparisonKey: 'chicken sausage',
    commodity: 'chicken sausage',
    attributes: [],
    sizeValue: 48,
    sizeUnit: 'oz',
    packCount: 4,
    baseUnit: 'OZ',
    baseQuantity: 48,
    chain: 'COSTCO',
    observedOn: '2026-10-01',
    priceCents: 1499,
    regularPriceCents: null,
    onSale: false,
    saleSignal: 'REGULAR',
    saleEndsOn: null,
    discontinued: false,
    unitPriceCents: null,
    itemNumber: '12345',
    photoUrl: null,
    confidence: null,
    needsReview: false,
    tagInsights: [],
    ...overrides,
  };
}

function match(overrides: Partial<DealMatch> = {}): DealMatch {
  return {
    productId: 10,
    productName: 'Kirkland Chicken Sausage',
    brand: 'Kirkland',
    itemNumber: '12345',
    dealTitle: 'Instant Savings',
    regularPriceCents: 1499,
    dealPriceCents: 1199,
    discountCents: 300,
    estimated: false,
    inWarehouse: true,
    validUntil: '2026-10-31',
    lastSeenOn: '2026-10-05',
    ...overrides,
  };
}

describe('rankGroup', () => {
  it('ranks cheapest per unit first with the percent above', () => {
    const cheap = sighting({ productId: 10, baseQuantity: 48, priceCents: 1440 });
    const dear = sighting({ productId: 11, baseQuantity: 48, priceCents: 2160 });
    const products = summarize([cheap, dear], '2026-10-06');
    const { ranked, unranked } = rankGroup(products, 'regular');

    expect(unranked).toEqual([]);
    expect(ranked.map((r) => r.product.productId)).toEqual([10, 11]);
    expect(ranked[0].percentAbove).toBe(0);
    expect(ranked[1].percentAbove).toBe(50);
  });

  it('leaves differently-sized products out of the ranking', () => {
    const sized = sighting({ productId: 10 });
    const unsized = sighting({ productId: 11, baseUnit: 'NONE', baseQuantity: null });
    const products = summarize([sized, unsized], '2026-10-06');
    const { ranked, unranked } = rankGroup(products, 'regular');

    expect(ranked.map((r) => r.product.productId)).toEqual([10]);
    expect(unranked.map((p) => p.productId)).toEqual([11]);
  });
});

describe('currentDeals', () => {
  it('merges a published offer with an in-store sale on the same product', () => {
    const tag = sighting({
      priceCents: 1199,
      regularPriceCents: 1499,
      saleEndsOn: '2026-10-31',
    });
    const products = summarize([tag], '2026-10-06');
    const [deal] = currentDeals(products, [match()], '2026-10-06');

    expect(deal.seenInStore).toBeTrue();
    expect(deal.listed).toBe('warehouse');
    // Costco's own figures win where both exist.
    expect(deal.dealCents).toBe(1199);
    expect(deal.endsOn).toBe('2026-10-31');
  });

  it('skips expired published offers and lists in-store sales alone', () => {
    const tag = sighting({
      priceCents: 1299,
      regularPriceCents: 1499,
      saleEndsOn: '2026-10-20',
    });
    const products = summarize([tag], '2026-10-06');
    const [deal] = currentDeals(products, [match({ validUntil: '2026-10-01' })], '2026-10-06');

    expect(deal.seenInStore).toBeTrue();
    expect(deal.listed).toBeNull();
    expect(deal.offCents).toBe(200);
  });

  it('sorts by end date, soonest first', () => {
    const soon = sighting({
      productId: 10,
      priceCents: 1299,
      regularPriceCents: 1499,
      saleEndsOn: '2026-10-08',
    });
    const later = sighting({
      productId: 11,
      productName: 'Eggs',
      priceCents: 399,
      regularPriceCents: 499,
      saleEndsOn: '2026-10-31',
    });
    const products = summarize([soon, later], '2026-10-06');
    const deals = currentDeals(products, [], '2026-10-06');

    expect(deals.map((d) => d.product.productId)).toEqual([10, 11]);
  });
});
