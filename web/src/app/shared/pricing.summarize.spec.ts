import { summarize } from './pricing';
import type { Observation } from '../core/models';

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

describe('summarize', () => {
  it('groups sightings by product, newest first', () => {
    const older = sighting({ id: 1, observedOn: '2026-09-01' });
    const newer = sighting({ id: 2, observedOn: '2026-10-01' });
    const other = sighting({ id: 3, productId: 11, productName: 'Eggs' });
    const summaries = summarize([older, newer, other], '2026-10-06');

    expect(summaries.length).toBe(2);
    const chicken = summaries.find((s) => s.productId === 10);
    expect(chicken?.entries.map((e) => e.id)).toEqual([2, 1]);
    expect(chicken?.latest.id).toBe(2);
  });

  it('rings up at the sale price while the sale runs', () => {
    const tag = sighting({
      priceCents: 1299,
      regularPriceCents: 1499,
      saleEndsOn: '2026-10-31',
    });
    const [summary] = summarize([tag], '2026-10-06');

    expect(summary.saleCents).toBe(1299);
    expect(summary.nowCents).toBe(1299);
    expect(summary.regularCents).toBe(1499);
    expect(summary.saleEndsOn).toBe('2026-10-31');
  });

  it('rings up at the regular price once the sale has ended', () => {
    const tag = sighting({
      priceCents: 1299,
      regularPriceCents: 1499,
      saleEndsOn: '2026-10-01',
    });
    const [summary] = summarize([tag], '2026-10-06');

    expect(summary.saleCents).toBeNull();
    expect(summary.nowCents).toBe(1499);
  });

  it('carries the latest review, discontinued, and sizing flags', () => {
    const older = sighting({ id: 1, needsReview: true, discontinued: true });
    const newer = sighting({ id: 2, needsReview: false, discontinued: false });
    const [summary] = summarize([older, newer], '2026-10-06');

    expect(summary.needsReview).toBeFalse();
    expect(summary.discontinued).toBeFalse();
    expect(summary.nowUnit).toBeCloseTo(1499 / 48, 5);
  });
});
