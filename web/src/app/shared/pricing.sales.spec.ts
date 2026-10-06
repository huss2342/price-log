import {
  isSale,
  perUnit,
  regularOf,
  saleRunning,
  wasPrice,
} from './pricing';
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

describe('sale arithmetic', () => {
  describe('wasPrice', () => {
    it('returns the regular price when it is higher than the paid price', () => {
      expect(wasPrice(sighting({ priceCents: 1299, regularPriceCents: 1499 }))).toBe(1499);
    });

    it('returns null when the regular price is not higher', () => {
      expect(wasPrice(sighting({ priceCents: 1499, regularPriceCents: 1499 }))).toBeNull();
      expect(wasPrice(sighting({ priceCents: 1499, regularPriceCents: 1299 }))).toBeNull();
      expect(wasPrice(sighting())).toBeNull();
    });
  });

  describe('isSale', () => {
    it('is true when the printed regular price beats the paid price', () => {
      expect(isSale(sighting({ priceCents: 1299, regularPriceCents: 1499 }))).toBeTrue();
    });

    it('is true when the tag says markdown even without a regular price', () => {
      expect(isSale(sighting({ onSale: true, saleSignal: 'MANAGER_MARKDOWN' }))).toBeTrue();
    });

    it('is false for an everyday price', () => {
      expect(isSale(sighting())).toBeFalse();
    });
  });

  describe('saleRunning', () => {
    it('runs while the printed end date has not passed', () => {
      const tag = sighting({ priceCents: 1299, regularPriceCents: 1499, saleEndsOn: '2026-10-31' });
      expect(saleRunning(tag, '2026-10-06')).toBeTrue();
      expect(saleRunning(tag, '2026-11-01')).toBeFalse();
    });

    it('treats an undated sale as running for about a month after the sighting', () => {
      const tag = sighting({ onSale: true });
      expect(saleRunning(tag, '2026-10-28')).toBeTrue();
      expect(saleRunning(tag, '2026-11-29')).toBeFalse();
    });

    it('is false when there is no sale at all', () => {
      expect(saleRunning(sighting(), '2026-10-06')).toBeFalse();
    });
  });

  describe('regularOf', () => {
    it('prefers the printed regular price over an older everyday sighting', () => {
      const newest = sighting({ id: 2, priceCents: 1299, regularPriceCents: 1499 });
      const older = sighting({ id: 1, observedOn: '2026-09-01', priceCents: 1399 });
      expect(regularOf([newest, older])).toBe(1499);
    });

    it('falls back to the newest non-sale sighting', () => {
      const newest = sighting({ id: 2, onSale: true });
      const older = sighting({ id: 1, observedOn: '2026-09-01', priceCents: 1399 });
      expect(regularOf([newest, older])).toBe(1399);
    });

    it('returns null when every sighting was a sale', () => {
      expect(regularOf([sighting({ onSale: true })])).toBeNull();
    });
  });

  describe('perUnit', () => {
    it('divides by the base quantity', () => {
      expect(perUnit(1499, 'OZ', 48)).toBeCloseTo(31.23, 2);
    });

    it('returns null when the size is unknown', () => {
      expect(perUnit(1499, 'NONE', null)).toBeNull();
      expect(perUnit(1499, 'OZ', null)).toBeNull();
      expect(perUnit(null, 'OZ', 48)).toBeNull();
    });
  });
});
