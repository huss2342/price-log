import type { BaseUnit, Chain, DealsView, Observation } from '../core/models';
import { isoToday } from '../shared/pricing';

/**
 * A worked example of a filled-in log, for showing the app to someone without
 * showing them a real household's shopping.
 *
 * <p>Invented, and labelled as invented wherever it is shown. The prices are
 * plausible rather than researched, so nobody should plan a trip around them.
 * They exist to make the behaviour legible: the same goods in different pack
 * sizes compared per unit, a sale that changes which is cheapest, a sale that
 * has ended, an item that is not being restocked, and a reading to check.
 */

const DAY = 86_400_000;

/** Dates are relative, so the demo never looks abandoned. */
function daysFromNow(n: number): string {
  return isoToday(new Date(Date.now() + n * DAY));
}

interface Sighting {
  /** Days before today the tag was photographed. */
  ago: number;
  price: number;
  /** The regular price printed beside a sale price. */
  regular?: number;
  /** Days from today the sale ends; negative once it has. */
  endsIn?: number;
  review?: boolean;
}

interface Seed {
  name: string;
  brand: string;
  category: string;
  commodity: string;
  attributes?: string[];
  chain: Chain;
  size: number;
  unit: string;
  pack?: number;
  baseUnit: BaseUnit;
  /** Total size in the base unit. */
  base: number;
  item: string;
  discontinued?: boolean;
  /** Newest first. */
  sightings: Sighting[];
}

const SEEDS: Seed[] = [
  {
    name: 'Kirkland Signature Italian Chicken Sausage',
    brand: 'Kirkland Signature',
    category: 'MEAT',
    commodity: 'chicken sausage',
    chain: 'COSTCO',
    size: 48,
    unit: 'oz',
    baseUnit: 'OZ',
    base: 48,
    item: '9000101',
    sightings: [
      { ago: 3, price: 1299 },
      { ago: 64, price: 1299 },
      { ago: 118, price: 999, regular: 1299, endsIn: -104 },
    ],
  },
  {
    name: 'Aidells Chicken & Apple Sausage',
    brand: 'Aidells',
    category: 'MEAT',
    commodity: 'chicken sausage',
    chain: 'COSTCO',
    size: 45,
    unit: 'oz',
    baseUnit: 'OZ',
    base: 45,
    item: '9000102',
    sightings: [
      { ago: 3, price: 1099, regular: 1499, endsIn: 6 },
      { ago: 50, price: 1499 },
    ],
  },
  {
    name: "Member's Mark Chicken Sausage",
    brand: "Member's Mark",
    category: 'MEAT',
    commodity: 'chicken sausage',
    chain: 'SAMS_CLUB',
    size: 40,
    unit: 'oz',
    baseUnit: 'OZ',
    base: 40,
    item: '9000103',
    sightings: [{ ago: 12, price: 1148 }],
  },
  {
    name: 'Kirkland Signature Organic Eggs',
    brand: 'Kirkland Signature',
    category: 'EGGS',
    commodity: 'large eggs',
    attributes: ['ORGANIC'],
    chain: 'COSTCO',
    size: 24,
    unit: 'ct',
    baseUnit: 'COUNT',
    base: 24,
    item: '9000104',
    sightings: [
      { ago: 4, price: 799 },
      { ago: 33, price: 849 },
    ],
  },
  {
    name: "Member's Mark Organic Eggs",
    brand: "Member's Mark",
    category: 'EGGS',
    commodity: 'large eggs',
    attributes: ['ORGANIC'],
    chain: 'SAMS_CLUB',
    size: 18,
    unit: 'ct',
    baseUnit: 'COUNT',
    base: 18,
    item: '9000105',
    sightings: [{ ago: 9, price: 719 }],
  },
  {
    name: 'Simply Nature Organic Eggs',
    brand: 'Simply Nature',
    category: 'EGGS',
    commodity: 'large eggs',
    attributes: ['ORGANIC'],
    chain: 'ALDI',
    size: 12,
    unit: 'ct',
    baseUnit: 'COUNT',
    base: 12,
    item: '9000106',
    sightings: [{ ago: 6, price: 529 }],
  },
  {
    name: 'Kirkland Signature Extra Virgin Olive Oil',
    brand: 'Kirkland Signature',
    category: 'PANTRY',
    commodity: 'olive oil',
    chain: 'COSTCO',
    size: 2,
    unit: 'l',
    baseUnit: 'FL_OZ',
    base: 67.63,
    item: '9000107',
    sightings: [
      { ago: 2, price: 1599, regular: 1899, endsIn: 11 },
      { ago: 40, price: 1899 },
    ],
  },
  {
    name: 'Specially Selected Extra Virgin Olive Oil',
    brand: 'Specially Selected',
    category: 'PANTRY',
    commodity: 'olive oil',
    chain: 'ALDI',
    size: 500,
    unit: 'ml',
    baseUnit: 'FL_OZ',
    base: 16.91,
    item: '9000108',
    sightings: [{ ago: 8, price: 699 }],
  },
  {
    name: 'Kirkland Signature Colombian Whole Bean Coffee',
    brand: 'Kirkland Signature',
    category: 'PANTRY',
    commodity: 'whole bean coffee',
    chain: 'COSTCO',
    size: 3,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 48,
    item: '9000109',
    sightings: [
      { ago: 21, price: 1799 },
      { ago: 95, price: 1799 },
    ],
  },
  {
    name: 'Charmin Ultra Soft Bath Tissue',
    brand: 'Charmin',
    category: 'PAPER_GOODS',
    commodity: 'bath tissue',
    chain: 'COSTCO',
    size: 30,
    unit: 'rolls',
    baseUnit: 'COUNT',
    base: 30,
    item: '9000110',
    sightings: [
      { ago: 5, price: 2499, regular: 3099, endsIn: 13 },
      { ago: 88, price: 3099 },
    ],
  },
  {
    name: "Member's Mark Ultra Soft Bath Tissue",
    brand: "Member's Mark",
    category: 'PAPER_GOODS',
    commodity: 'bath tissue',
    chain: 'SAMS_CLUB',
    size: 45,
    unit: 'rolls',
    baseUnit: 'COUNT',
    base: 45,
    item: '9000111',
    sightings: [{ ago: 11, price: 2848 }],
  },
  {
    name: 'Kirkland Signature In-Shell Pistachios',
    brand: 'Kirkland Signature',
    category: 'SNACKS',
    commodity: 'pistachios',
    chain: 'COSTCO',
    size: 3,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 48,
    item: '9000112',
    discontinued: true,
    sightings: [{ ago: 1, price: 1999 }],
  },
  {
    name: 'Kirkland Signature Unsalted Butter',
    brand: 'Kirkland Signature',
    category: 'DAIRY',
    commodity: 'butter',
    chain: 'COSTCO',
    size: 4,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 64,
    item: '9000113',
    sightings: [{ ago: 16, price: 1649 }],
  },
  {
    name: 'Countryside Creamery Unsalted Butter',
    brand: 'Countryside Creamery',
    category: 'DAIRY',
    commodity: 'butter',
    chain: 'ALDI',
    size: 1,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 16,
    item: '9000114',
    sightings: [{ ago: 3, price: 419 }],
  },
  {
    name: 'LaCroix Sparkling Water',
    brand: 'LaCroix',
    category: 'BEVERAGES',
    commodity: 'sparkling water',
    chain: 'COSTCO',
    size: 12,
    unit: 'fl oz',
    pack: 24,
    baseUnit: 'FL_OZ',
    base: 288,
    item: '9000115',
    sightings: [{ ago: 7, price: 1099, regular: 1349, endsIn: -2 }],
  },
  {
    name: "Member's Mark Frozen Blueberries",
    brand: "Member's Mark",
    category: 'FROZEN',
    commodity: 'frozen blueberries',
    chain: 'SAMS_CLUB',
    size: 4,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 64,
    item: '9000116',
    sightings: [{ ago: 0, price: 1398, review: true }],
  },
  {
    name: 'Kirkland Signature Frozen Blueberries',
    brand: 'Kirkland Signature',
    category: 'FROZEN',
    commodity: 'frozen blueberries',
    chain: 'COSTCO',
    size: 3,
    unit: 'lb',
    baseUnit: 'OZ',
    base: 48,
    item: '9000117',
    sightings: [{ ago: 14, price: 1099 }],
  },
];

function slug(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

function productId(name: string): number {
  return SEEDS.findIndex((seed) => seed.name === name) + 1;
}

export const DEMO_ENTRIES: Observation[] = SEEDS.flatMap((seed, index) => {
  const attributes = seed.attributes ?? [];
  const comparisonKey = [
    seed.category,
    slug(seed.commodity),
    attributes.length ? [...attributes].sort().join('+') : 'plain',
  ].join('|');

  return seed.sightings.map((s, n): Observation => {
    const onSale = s.regular != null;
    const discontinued = !!seed.discontinued && n === 0;
    return {
      id: 0,
      productId: index + 1,
      productName: seed.name,
      brand: seed.brand,
      category: seed.category,
      comparisonKey,
      commodity: seed.commodity,
      attributes,
      sizeValue: seed.size,
      sizeUnit: seed.unit,
      packCount: seed.pack ?? 1,
      baseUnit: seed.baseUnit,
      baseQuantity: seed.base,
      chain: seed.chain,
      observedOn: daysFromNow(-s.ago),
      priceCents: s.price,
      regularPriceCents: s.regular ?? null,
      onSale,
      saleSignal: discontinued ? 'DISCONTINUED' : onSale ? 'INSTANT_SAVINGS' : 'REGULAR',
      saleEndsOn: s.endsIn == null ? null : daysFromNow(s.endsIn),
      discontinued,
      unitPriceCents: s.price / seed.base,
      itemNumber: seed.item,
      photoUrl: null,
      confidence: s.review ? 0.62 : 0.95,
      needsReview: !!s.review,
      tagInsights: discontinued ? ['Asterisk in the corner: the warehouse is not reordering it.'] : [],
    };
  });
})
  // Newest sighting gets the highest id, the way the real log numbers captures.
  .sort((a, b) => b.observedOn.localeCompare(a.observedOn))
  .map((entry, i, all) => ({ ...entry, id: all.length - i }));

export const DEMO_DEALS: DealsView = {
  matches: [
    {
      productId: productId('Kirkland Signature Colombian Whole Bean Coffee'),
      productName: 'Kirkland Signature Colombian Whole Bean Coffee',
      brand: 'Kirkland Signature',
      itemNumber: '9000109',
      dealTitle: 'Kirkland Signature Colombian Whole Bean Coffee, 3 lb',
      regularPriceCents: 1799,
      dealPriceCents: 1399,
      discountCents: 400,
      estimated: true,
      inWarehouse: true,
      validUntil: daysFromNow(6),
      lastSeenOn: daysFromNow(-21),
    },
    {
      productId: productId('Charmin Ultra Soft Bath Tissue'),
      productName: 'Charmin Ultra Soft Bath Tissue',
      brand: 'Charmin',
      itemNumber: '9000110',
      dealTitle: 'Charmin Ultra Soft Bath Tissue, 30 Rolls',
      regularPriceCents: 3099,
      dealPriceCents: 2499,
      discountCents: 600,
      estimated: true,
      inWarehouse: true,
      validUntil: daysFromNow(13),
      lastSeenOn: daysFromNow(-5),
    },
  ],
  totalDeals: 176,
  lastCheckedAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
  error: null,
};
