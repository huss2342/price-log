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
