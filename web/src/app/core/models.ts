export type Chain = 'COSTCO' | 'SAMS_CLUB' | 'ALDI' | 'WALMART' | 'OTHER';

export type SaleSignal =
  | 'REGULAR'
  | 'INSTANT_SAVINGS'
  | 'CLEARANCE'
  | 'MANAGER_MARKDOWN'
  | 'DISCONTINUED'
  | 'UNKNOWN';

export type BaseUnit = 'OZ' | 'FL_OZ' | 'COUNT' | 'NONE';

/** A named place a chain operates, as the user calls it: "Costco — Manchester". */
export interface Store {
  id: number;
  chain: Chain;
  label: string;
  city: string | null;
  state: string | null;
  createdAt: string;
  updatedAt: string;
  observationCount: number;
}

/** One price tag, seen at one chain, on one day. */
export interface Observation {
  id: number;
  productId: number;
  productName: string;
  brand: string | null;
  category: string;
  /** Products sharing this are compared with each other. */
  comparisonKey: string;
  /** What the item is, without brand or size: "chicken sausage". */
  commodity: string;
  attributes: string[];
  sizeValue: number | null;
  sizeUnit: string | null;
  packCount: number | null;
  baseUnit: BaseUnit;
  baseQuantity: number | null;
  chain: Chain;
  /** The named store chosen at capture, when one was chosen. Additive: an older API simply omits it. */
  storeId?: number | null;
  storeLabel?: string | null;
  observedOn: string;
  priceCents: number;
  regularPriceCents: number | null;
  onSale: boolean;
  saleSignal: SaleSignal;
  saleEndsOn: string | null;
  discontinued: boolean;
  unitPriceCents: number | null;
  itemNumber: string | null;
  photoUrl: string | null;
  confidence: number | null;
  needsReview: boolean;
  /** Plain-language readings of every tag rule that matched. */
  tagInsights: string[];
}

/** A promotion Costco publishes, matched to something already logged by item number. */
export interface DealMatch {
  productId: number;
  productName: string;
  brand: string | null;
  itemNumber: string;
  dealTitle: string;
  /** What it costs without the promotion, from the log. */
  regularPriceCents: number | null;
  /** What it rings up at during the promotion. */
  dealPriceCents: number | null;
  discountCents: number | null;
  /** True when a figure was worked out from the log rather than printed. */
  estimated: boolean;
  inWarehouse: boolean;
  validUntil: string | null;
  lastSeenOn: string;
}

export interface DealsView {
  matches: DealMatch[];
  totalDeals: number;
  lastCheckedAt: string | null;
  error: string | null;
}

export interface TagRule {
  id: number;
  chain: Chain;
  matchType: string;
  pattern: string;
  signal: SaleSignal;
  meaning: string;
  advice: string | null;
  priority: number;
  enabled: boolean;
}

export interface ObservationUpdate {
  productName?: string;
  brand?: string | null;
  commodity?: string;
  category?: string;
  sizeValue?: number | null;
  sizeUnit?: string | null;
  packCount?: number | null;
  chain?: Chain;
  priceCents?: number;
  regularPriceCents?: number | null;
  onSale?: boolean;
  saleEndsOn?: string | null;
  discontinued?: boolean;
  reviewed?: boolean;
}

export const CHAINS: Chain[] = ['COSTCO', 'SAMS_CLUB', 'ALDI', 'WALMART', 'OTHER'];

export const CHAIN_LABELS: Record<Chain, string> = {
  COSTCO: 'Costco',
  SAMS_CLUB: "Sam's Club",
  ALDI: 'Aldi',
  WALMART: 'Walmart',
  OTHER: 'Other',
};

/** In roughly the order a grocery trip goes. */
export const CATEGORIES = [
  'PRODUCE',
  'MEAT',
  'SEAFOOD',
  'DAIRY',
  'EGGS',
  'BAKERY',
  'PANTRY',
  'SNACKS',
  'BEVERAGES',
  'FROZEN',
  'HOUSEHOLD',
  'PAPER_GOODS',
  'PERSONAL_CARE',
  'SUPPLEMENTS',
  'PET',
  'HOME',
  'ELECTRONICS',
  'APPAREL',
  'OTHER',
] as const;

export function categoryLabel(category: string): string {
  const words = category.replaceAll('_', ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const UNIT_LABELS: Record<BaseUnit, string> = {
  OZ: 'oz',
  FL_OZ: 'fl oz',
  COUNT: 'ea',
  NONE: '',
};
