export type Chain = 'COSTCO' | 'SAMS_CLUB' | 'ALDI' | 'WALMART' | 'OTHER';

export type SaleSignal =
  | 'REGULAR'
  | 'INSTANT_SAVINGS'
  | 'CLEARANCE'
  | 'MANAGER_MARKDOWN'
  | 'DISCONTINUED'
  | 'UNKNOWN';

export type BaseUnit = 'OZ' | 'FL_OZ' | 'COUNT' | 'NONE';

export interface Store {
  id: number;
  chain: Chain;
  label: string;
  city: string | null;
  state: string | null;
}

export interface Observation {
  id: number;
  productId: number;
  productName: string;
  brand: string | null;
  category: string;
  attributes: string[];
  sizeValue: number | null;
  sizeUnit: string | null;
  packCount: number | null;
  baseUnit: BaseUnit;
  baseQuantity: number | null;
  storeId: number;
  chain: Chain;
  storeLabel: string;
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
  notes: string | null;
  /** Plain-language readings of every tag rule that matched. */
  tagInsights: string[];
  advice: string | null;

  // How this price compares to the past. Always present, because "is this
  // cheaper than last time" is the question being asked at the shelf.
  watched: boolean;
  targetPriceCents: number | null;
  previousPriceCents: number | null;
  previousObservedOn: string | null;
  /** Negative means it got cheaper since the last sighting. */
  changeCents: number | null;
  changePercent: number | null;
  lowestBeforeCents: number | null;
  lowestEver: boolean;
  meetsTarget: boolean;
}

export interface DealMatch {
  productId: number;
  productName: string;
  brand: string | null;
  itemNumber: string;
  dealTitle: string;
  /** What Costco says it costs during the promotion. */
  salePriceCents: number | null;
  /** What Costco says is being taken off. Separate from the price. */
  discountCents: number | null;
  inWarehouse: boolean;
  watched: boolean;
  lastPriceCents: number | null;
  /** Last price minus the published discount; an estimate, not a quote. */
  impliedPriceCents: number | null;
  lastSeenOn: string;
  daysSinceSeen: number;
}

export interface DealsView {
  matches: DealMatch[];
  totalDeals: number;
  lastCheckedAt: string | null;
  error: string | null;
}

export interface SaleCycle {
  salesSeen: number;
  averageGapDays: number | null;
  lastSaleOn: string | null;
  daysSinceLastSale: number | null;
  /** Negative when the next sale already looks overdue. */
  dueInDays: number | null;
  confident: boolean;
}

export interface WatchedItem {
  productId: number;
  productName: string;
  brand: string | null;
  itemNumber: string | null;
  targetPriceCents: number | null;
  lastPriceCents: number | null;
  bestPriceCents: number | null;
  lastSeenOn: string;
  daysSinceSeen: number;
  meetsTarget: boolean;
  cycle: SaleCycle;
  dueForSale: boolean;
}

export interface StoreOffer {
  observationId: number;
  storeId: number;
  chain: Chain;
  storeLabel: string;
  productId: number;
  productName: string;
  brand: string | null;
  priceCents: number;
  regularPriceCents: number | null;
  onSale: boolean;
  saleSignal: SaleSignal;
  discontinued: boolean;
  unitPriceCents: number | null;
  baseUnit: BaseUnit;
  observedOn: string;
  daysAgo: number;
  stale: boolean;
  percentAboveBest: number | null;
}

export interface CompareGroup {
  comparisonKey: string;
  label: string;
  category: string;
  attributes: string[];
  baseUnit: BaseUnit;
  /** Cheapest first. */
  offers: StoreOffer[];
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
  category?: string;
  attributes?: string[];
  sizeValue?: number | null;
  sizeUnit?: string | null;
  packCount?: number | null;
  storeId?: number;
  observedOn?: string;
  priceCents?: number;
  regularPriceCents?: number | null;
  onSale?: boolean;
  saleSignal?: SaleSignal;
  discontinued?: boolean;
  notes?: string | null;
  reviewed?: boolean;
}

export const CHAIN_LABELS: Record<Chain, string> = {
  COSTCO: 'Costco',
  SAMS_CLUB: "Sam's Club",
  ALDI: 'Aldi',
  WALMART: 'Walmart',
  OTHER: 'Other',
};

/** Short label plus the tone the badge should carry. */
export const SIGNAL_LABELS: Record<SaleSignal, { text: string; tone: string }> = {
  REGULAR: { text: 'Regular price', tone: 'neutral' },
  INSTANT_SAVINGS: { text: 'Instant savings', tone: 'info' },
  CLEARANCE: { text: 'Clearance', tone: 'warn' },
  MANAGER_MARKDOWN: { text: 'Manager markdown', tone: 'good' },
  DISCONTINUED: { text: 'Not restocking', tone: 'urgent' },
  UNKNOWN: { text: 'Unread tag', tone: 'neutral' },
};

export const UNIT_LABELS: Record<BaseUnit, string> = {
  OZ: 'oz',
  FL_OZ: 'fl oz',
  COUNT: 'ea',
  NONE: '',
};
