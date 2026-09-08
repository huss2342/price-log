import type {
  BrowsedDeal,
  CompareGroup,
  DealsView,
  SaleSignal,
  Store,
  StoreOffer,
  WatchedItem,
} from '../core/models';

/**
 * A worked example of a filled-in log, for showing the app to someone without
 * showing them a real household's shopping.
 *
 * <p>Invented, and labelled as invented wherever it is shown. The prices are
 * plausible rather than researched, so nobody should plan a trip around them.
 * They exist to make the comparison behaviour legible: the same goods at
 * different clubs in different pack sizes, which is the entire point of the app
 * and is invisible on an empty log.
 */

const DAY = 86_400_000;

/** Dates are relative, so the demo never looks abandoned. */
function daysAgo(n: number): string {
  return new Date(Date.now() - n * DAY).toISOString().slice(0, 10);
}

export const DEMO_STORES: Store[] = [
  { id: 1, chain: 'COSTCO', label: 'Costco', city: 'Saint Louis', state: 'MO' },
  { id: 2, chain: 'SAMS_CLUB', label: 'Sam’s Club', city: 'Saint Louis', state: 'MO' },
  { id: 3, chain: 'ALDI', label: 'Aldi', city: 'Saint Louis', state: 'MO' },
];

type StoreKey = 1 | 2 | 3;

interface OfferSeed {
  store: StoreKey;
  price: number;
  regular?: number;
  size: number;
  signal?: SaleSignal;
  days: number;
}

interface GroupSeed {
  key: string;
  label: string;
  category: string;
  attributes?: string[];
  unit: 'OZ' | 'FL_OZ' | 'COUNT';
  brandBy: Partial<Record<StoreKey, string>>;
  nameBy: Partial<Record<StoreKey, string>>;
  offers: OfferSeed[];
}

const CHAINS = { 1: 'COSTCO', 2: 'SAMS_CLUB', 3: 'ALDI' } as const;

const SEEDS: GroupSeed[] = [
  {
    key: 'EGGS|eggs-large|organic,pasture-raised',
    label: 'Eggs, large — organic, pasture-raised',
    category: 'EGGS',
    attributes: ['organic', 'pasture-raised'],
    unit: 'COUNT',
    brandBy: { 1: 'Kirkland Signature', 2: 'Member’s Mark', 3: 'Simply Nature' },
    nameBy: {
      1: 'KS ORGANIC EGGS 24CT',
      2: 'MM ORGANIC PASTURE EGGS 18CT',
      3: 'SIMPLY NATURE ORGANIC EGGS 12CT',
    },
    offers: [
      { store: 1, price: 799, size: 24, days: 4 },
      { store: 2, price: 719, size: 18, days: 9 },
      { store: 3, price: 529, size: 12, days: 6 },
    ],
  },
  {
    key: 'PAPER_GOODS|bath-tissue|plain',
    label: 'Bath tissue',
    category: 'PAPER_GOODS',
    unit: 'COUNT',
    brandBy: { 1: 'Charmin', 2: 'Member’s Mark' },
    nameBy: { 1: 'CHARMIN ULTRA SOFT 30 ROLLS', 2: 'MM ULTRA SOFT 45 ROLLS' },
    offers: [
      { store: 1, price: 2499, regular: 3099, size: 30, signal: 'INSTANT_SAVINGS', days: 2 },
      { store: 2, price: 2848, size: 45, days: 11 },
    ],
  },
  {
    key: 'PANTRY|olive-oil|extra-virgin',
    label: 'Olive oil — extra virgin',
    category: 'PANTRY',
    attributes: ['extra-virgin'],
    unit: 'FL_OZ',
    brandBy: { 1: 'Kirkland Signature', 3: 'Specially Selected' },
    nameBy: { 1: 'KS ORGANIC EVOO 2L', 3: 'SPECIALLY SELECTED EVOO 500ML' },
    offers: [
      { store: 1, price: 1899, size: 67.6, days: 21 },
      { store: 3, price: 699, size: 16.9, days: 8 },
    ],
  },
  {
    key: 'MEAT|chicken-breast|boneless-skinless',
    label: 'Chicken breast — boneless, skinless',
    category: 'MEAT',
    attributes: ['boneless-skinless'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 2: 'Member’s Mark', 3: 'Never Any!' },
    nameBy: {
      1: 'KS CHICKEN BREAST 6.5LB',
      2: 'MM CHICKEN BREAST 5LB',
      3: 'NEVER ANY! CHICKEN BREAST 3LB',
    },
    offers: [
      { store: 1, price: 2274, size: 104, days: 5 },
      { store: 2, price: 1898, size: 80, days: 13 },
      { store: 3, price: 1497, size: 48, days: 7 },
    ],
  },
  {
    key: 'DAIRY|butter|unsalted',
    label: 'Butter — unsalted',
    category: 'DAIRY',
    attributes: ['unsalted'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 3: 'Countryside Creamery' },
    nameBy: { 1: 'KS UNSALTED BUTTER 4LB', 3: 'COUNTRYSIDE UNSALTED BUTTER 1LB' },
    offers: [
      { store: 1, price: 1649, size: 64, days: 16 },
      { store: 3, price: 419, size: 16, days: 3 },
    ],
  },
  {
    key: 'PANTRY|coffee|whole-bean',
    label: 'Coffee — whole bean',
    category: 'PANTRY',
    attributes: ['whole-bean'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 2: 'Member’s Mark' },
    nameBy: { 1: 'KS COLOMBIAN WHOLE BEAN 3LB', 2: 'MM WHOLE BEAN COLOMBIAN 40OZ' },
    offers: [
      { store: 1, price: 1799, regular: 2199, size: 48, signal: 'MANAGER_MARKDOWN', days: 1 },
      { store: 2, price: 1798, size: 40, days: 19 },
    ],
  },
  {
    key: 'SNACKS|mixed-nuts|unsalted',
    label: 'Mixed nuts — unsalted',
    category: 'SNACKS',
    attributes: ['unsalted'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 2: 'Member’s Mark' },
    nameBy: { 1: 'KS UNSALTED MIXED NUTS 40OZ', 2: 'MM UNSALTED MIXED NUTS 34OZ' },
    offers: [
      { store: 1, price: 2299, size: 40, days: 34 },
      { store: 2, price: 2148, size: 34, days: 27 },
    ],
  },
  {
    key: 'SEAFOOD|salmon-fillet|farmed-atlantic',
    label: 'Salmon fillet — farmed Atlantic',
    category: 'SEAFOOD',
    attributes: ['farmed-atlantic'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 3: 'Fremont Fish Market' },
    nameBy: { 1: 'KS ATLANTIC SALMON 3LB', 3: 'FREMONT ATLANTIC SALMON 16OZ' },
    offers: [
      { store: 1, price: 3399, size: 48, days: 6 },
      { store: 3, price: 1299, size: 16, days: 10 },
    ],
  },
  {
    key: 'PRODUCE|baby-spinach|organic',
    label: 'Baby spinach — organic',
    category: 'PRODUCE',
    attributes: ['organic'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 3: 'Simply Nature' },
    nameBy: { 1: 'KS ORGANIC BABY SPINACH 1LB', 3: 'SIMPLY NATURE SPINACH 5OZ' },
    offers: [
      { store: 1, price: 549, size: 16, days: 3 },
      { store: 3, price: 279, size: 5, days: 5 },
    ],
  },
  {
    key: 'FROZEN|blueberries|frozen-whole',
    label: 'Blueberries — frozen',
    category: 'FROZEN',
    attributes: ['frozen-whole'],
    unit: 'OZ',
    brandBy: { 1: 'Kirkland Signature', 2: 'Member’s Mark' },
    nameBy: { 1: 'KS FROZEN BLUEBERRIES 3LB', 2: 'MM FROZEN BLUEBERRIES 4LB' },
    offers: [
      { store: 1, price: 1099, size: 48, days: 14 },
      { store: 2, price: 1398, size: 64, days: 22 },
    ],
  },
  {
    key: 'BEVERAGES|sparkling-water|unsweetened',
    label: 'Sparkling water — unsweetened',
    category: 'BEVERAGES',
    attributes: ['unsweetened'],
    unit: 'FL_OZ',
    brandBy: { 1: 'LaCroix', 3: 'Summit' },
    nameBy: { 1: 'LACROIX 24PK 12OZ', 3: 'SUMMIT SPARKLING 12PK 12OZ' },
    offers: [
      { store: 1, price: 1099, regular: 1349, size: 288, signal: 'INSTANT_SAVINGS', days: 7 },
      { store: 3, price: 349, size: 144, days: 9 },
    ],
  },
  {
    key: 'HOUSEHOLD|dish-soap|plain',
    label: 'Dish soap',
    category: 'HOUSEHOLD',
    unit: 'FL_OZ',
    brandBy: { 1: 'Dawn', 3: 'Aldi' },
    nameBy: { 1: 'DAWN ULTRA 3PK 56OZ EA', 3: 'ALDI DISH SOAP 24OZ' },
    offers: [
      { store: 1, price: 1399, size: 168, days: 41 },
      { store: 3, price: 199, size: 24, days: 12 },
    ],
  },
];

function buildOffers(seed: GroupSeed, groupIndex: number): StoreOffer[] {
  const offers = seed.offers.map((o, index) => {
    return {
      observationId: groupIndex * 10 + index + 1,
      storeId: o.store,
      chain: CHAINS[o.store],
      storeLabel: DEMO_STORES[o.store - 1].label,
      productId: groupIndex * 10 + o.store,
      productName: seed.nameBy[o.store] ?? seed.label,
      brand: seed.brandBy[o.store] ?? null,
      priceCents: o.price,
      regularPriceCents: o.regular ?? null,
      onSale: o.regular != null,
      saleSignal: o.signal ?? 'REGULAR',
      discontinued: false,
      unitPriceCents: o.price / o.size,
      baseUnit: seed.unit,
      observedOn: daysAgo(o.days),
      daysAgo: o.days,
      stale: o.days > 60,
      percentAboveBest: 0,
    } satisfies StoreOffer;
  });

  // Cheapest per unit first, each carrying how far off the best it is. This is
  // the ranking the real service does; the demo has to show the same thing.
  offers.sort((a, b) => (a.unitPriceCents ?? 0) - (b.unitPriceCents ?? 0));
  const best = offers[0]?.unitPriceCents ?? 0;
  for (const offer of offers) {
    // One decimal, because that is what the API returns -- the template prints
    // this value as-is, so a raw float renders as +19.983312473925743%.
    offer.percentAboveBest =
      best > 0 && offer.unitPriceCents != null
        ? Math.round(((offer.unitPriceCents - best) / best) * 1000) / 10
        : 0;
  }
  return offers;
}

export const DEMO_GROUPS: CompareGroup[] = SEEDS.map((seed, index) => ({
  comparisonKey: seed.key,
  label: seed.label,
  category: seed.category,
  attributes: seed.attributes ?? [],
  baseUnit: seed.unit,
  offers: buildOffers(seed, index),
}));

export const DEMO_WATCHLIST: WatchedItem[] = [
  {
    productId: 11,
    productName: 'CHARMIN ULTRA SOFT 30 ROLLS',
    brand: 'Charmin',
    itemNumber: '2048748',
    targetPriceCents: 2200,
    lastPriceCents: 2499,
    bestPriceCents: 2299,
    lastSeenOn: daysAgo(2),
    daysSinceSeen: 2,
    meetsTarget: false,
    cycle: {
      salesSeen: 3,
      averageGapDays: 91,
      lastSaleOn: daysAgo(84),
      daysSinceLastSale: 84,
      dueInDays: 7,
      confident: true,
    },
    dueForSale: false,
  },
  {
    productId: 51,
    productName: 'KS COLOMBIAN WHOLE BEAN 3LB',
    brand: 'Kirkland Signature',
    itemNumber: '1030984',
    targetPriceCents: 1800,
    lastPriceCents: 1799,
    bestPriceCents: 1799,
    lastSeenOn: daysAgo(1),
    daysSinceSeen: 1,
    meetsTarget: true,
    cycle: {
      salesSeen: 2,
      averageGapDays: 120,
      lastSaleOn: daysAgo(1),
      daysSinceLastSale: 1,
      dueInDays: 119,
      confident: false,
    },
    dueForSale: false,
  },
];

export const DEMO_DEALS: DealsView = {
  matches: [
    {
      productId: 11,
      productName: 'CHARMIN ULTRA SOFT 30 ROLLS',
      brand: 'Charmin',
      itemNumber: '2048748',
      dealTitle: 'Charmin Ultra Soft Bath Tissue, 30 Rolls',
      salePriceCents: 2499,
      discountCents: 600,
      inWarehouse: true,
      watched: true,
      lastPriceCents: 3099,
      impliedPriceCents: 2499,
      lastSeenOn: daysAgo(2),
      daysSinceSeen: 2,
    },
    {
      productId: 51,
      productName: 'KS COLOMBIAN WHOLE BEAN 3LB',
      brand: 'Kirkland Signature',
      itemNumber: '1030984',
      dealTitle: 'Kirkland Signature Colombian Whole Bean Coffee, 3 lb',
      salePriceCents: 1799,
      discountCents: 400,
      inWarehouse: true,
      watched: true,
      lastPriceCents: 2199,
      impliedPriceCents: 1799,
      lastSeenOn: daysAgo(1),
      daysSinceSeen: 1,
    },
  ],
  totalDeals: 190,
  lastCheckedAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
  error: null,
};

const PUBLISHED: Array<[string, string, number, number | null, boolean, boolean]> = [
  ['2048748', 'Charmin Ultra Soft Bath Tissue, 30 Rolls', 2499, 600, true, true],
  ['1030984', 'Kirkland Signature Colombian Whole Bean Coffee, 3 lb', 1799, 400, true, true],
  ['1655513', 'Kirkland Signature Organic Extra Virgin Olive Oil, 2 L', 1899, 300, true, false],
  ['9203847', 'Dyson V15 Detect Cordless Vacuum', 49999, 15000, false, false],
  ['4471028', 'Ninja Foodi 8-Quart Pressure Cooker', 12999, 4000, true, false],
  ['7719302', 'Bose QuietComfort Ultra Headphones', 32999, 7000, false, false],
  ['3308174', 'Kirkland Signature Bacon, 4-Pack', 1899, 250, true, false],
  ['5582910', 'Greenworks 80V Cordless Snow Blower', 59999, 20000, true, false],
  ['6640281', 'Samsonite Omni 3-Piece Luggage Set', 17999, 5000, false, false],
  ['2214506', 'Kirkland Signature Laundry Detergent, 194 fl oz', 1699, 400, true, false],
  ['8873019', 'Michelin Defender Tires, Set of 4', 74999, 15000, true, false],
  ['1192847', 'Kirkland Signature Almond Butter, 27 oz', 899, null, true, false],
];

export const DEMO_PUBLISHED_DEALS: BrowsedDeal[] = PUBLISHED.map(
  ([itemNumber, title, sale, discount, inWarehouse, logged]) => ({
    itemNumber,
    title,
    salePriceCents: sale,
    discountCents: discount,
    inWarehouse,
    logged,
    firstSeenOn: daysAgo(9),
    lastSeenOn: daysAgo(0),
  }),
);
