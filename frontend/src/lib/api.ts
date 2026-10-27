import type { Reason } from "@/lib/i18n/reasons";

// Server-only: API_BASE_URL has no NEXT_PUBLIC_ prefix, so it never reaches the browser.
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

// Mirrors the Java records in backend/.../market. Nullable means "not available", not zero.
export type StockQuote = {
  ticker: string;
  name: string;
  sector: string | null;
  tradeDate: string | null; // YYYY-MM-DD; null for demo data
  lastPrice: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  volume: number | null;
  value: number | null; // estimated: last price x volume
  marketCap: number | null; // IDR, from the weekly listing sync
  brokerTracked: boolean; // broker summaries are imported for this stock
  hsc: boolean; // on IDX's High Shareholding Concentration list
};

export type IndexSnapshot = {
  code: string;
  name: string;
  tradeDate: string | null;
  value: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
};

export type MarketOverview = {
  demo: boolean;
  dataSource: string;
  notice: string | null;
  generatedAt: string;
  dataDate: string | null; // trading day the data is from; null for demo data
  compositeIndex: IndexSnapshot | null; // null until the index is imported
  advancers: number;
  decliners: number;
  unchanged: number;
  topGainers: StockQuote[];
  topLosers: StockQuote[];
  mostActive: StockQuote[];
  sparklines: Record<string, number[]>; // last ~20 closes per listed ticker, oldest first
};

type DataLabel = {
  demo: boolean;
  dataSource: string;
  notice: string | null;
  generatedAt: string;
  dataDate: string | null; // trading day the data is from; null for demo data
};

export type StockDetail = DataLabel & { quote: StockQuote };

export type PriceBar = {
  date: string; // YYYY-MM-DD trading day
  open: number | null;
  high: number | null;
  low: number | null;
  close: number;
  volume: number | null;
};

export const PRICE_RANGES = ["1M", "3M", "6M", "1Y"] as const;
export type PriceRange = (typeof PRICE_RANGES)[number];

export type PriceHistory = DataLabel & {
  ticker: string;
  range: PriceRange;
  bars: PriceBar[];
};

// Volumes in shares (1 lot = 100 shares), values in IDR.
export type BrokerPosition = {
  brokerCode: string;
  brokerName: string | null;
  buyVolume: number;
  buyValue: number;
  buyAveragePrice: number | null;
  sellVolume: number;
  sellValue: number;
  sellAveragePrice: number | null;
  netVolume: number;
  netValue: number;
  foreignNetValue: number | null; // foreign investors via this broker; null = not known for every day
};

export type DailyBrokerFlow = {
  date: string;
  totalVolume: number;
  top5NetBuyVolume: number;
  top5NetSellVolume: number;
  concentration: number; // fraction of total volume, e.g. 0.06 = 6%
  buyerCount: number;
  sellerCount: number;
  foreignNetValue: number | null; // null = foreign flow not available that day
  cumulativeForeignNetValue: number | null;
};

export type FlowStatus = "ACCUMULATION" | "NEUTRAL" | "DISTRIBUTION";

export type BrokerSummary = Omit<DataLabel, "dataDate"> & {
  ticker: string;
  range: PriceRange;
  tracked: boolean; // false: broker data isn't imported for this stock at all
  from: string;
  to: string;
  tradingDays: number; // trading days in the range
  brokerDays: number; // of those, days with broker data (fewer while history is still importing)
  lastPrice: number | null;
  assessment: { status: FlowStatus; reasons: Reason[] };
  topBuyers: BrokerPosition[];
  topSellers: BrokerPosition[];
  daily: DailyBrokerFlow[];
};

export const STOCK_SORTS = ["marketCap", "change", "value", "volume", "price", "ticker"] as const;
export type StockSort = (typeof STOCK_SORTS)[number];

export type StockList = DataLabel & {
  total: number; // matching stocks across all pages
  page: number;
  size: number;
  sectors: string[];
  stocks: StockQuote[];
};

export type StockListQuery = {
  search?: string;
  sector?: string;
  change?: "up" | "down" | "flat";
  hsc?: "only" | "exclude";
  sort?: StockSort;
  order?: "asc" | "desc";
  page?: number;
};

export async function getStockList(query: StockListQuery): Promise<StockList> {
  const params = new URLSearchParams();
  if (query.search) params.set("search", query.search);
  if (query.sector) params.set("sector", query.sector);
  if (query.change) params.set("change", query.change);
  if (query.hsc) params.set("hsc", query.hsc);
  params.set("sort", query.sort ?? "marketCap");
  params.set("order", query.order ?? "desc");
  params.set("page", String(query.page ?? 1));
  params.set("size", "50");
  const response = await fetch(`${API_BASE_URL}/api/stocks?${params}`);
  if (!response.ok) {
    throw new Error(`Stock list request failed: ${response.status}`);
  }
  return response.json();
}

export const INTERVALS = ["15m", "1h", "4h", "1d", "1w", "1mo"] as const;
export type Interval = (typeof INTERVALS)[number];

/** time = candle start, epoch seconds UTC. Daily+ candles start at 00:00 UTC of their trading date. */
export type Candle = {
  time: number;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number;
  volume: number | null;
};

export type CandleSeries = Omit<DataLabel, "dataDate"> & {
  symbol: string;
  interval: Interval;
  range: string;
  ranges: string[]; // ranges offered for this interval
  intraday: boolean; // fetched live from the source, possibly delayed
  adjusted: boolean; // adjusted for splits and dividends
  candles: Candle[];
};

async function getCandles(path: string, interval: Interval, range?: string): Promise<CandleSeries | null> {
  const params = new URLSearchParams({ interval });
  if (range) params.set("range", range);
  const response = await fetch(`${API_BASE_URL}${path}/candles?${params}`);
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Candles request failed: ${response.status}`);
  }
  return response.json();
}

export function getStockCandles(ticker: string, interval: Interval, range?: string) {
  return getCandles(`/api/stocks/${encodeURIComponent(ticker)}`, interval, range);
}

export function getIndexCandles(interval: Interval, range?: string) {
  return getCandles(`/api/index/COMPOSITE`, interval, range);
}

export type IndexDetail = Omit<DataLabel, "dataDate"> & { index: IndexSnapshot | null };

export async function getIndex(): Promise<IndexDetail> {
  const response = await fetch(`${API_BASE_URL}/api/index/COMPOSITE`);
  if (!response.ok) {
    throw new Error(`Index request failed: ${response.status}`);
  }
  return response.json();
}

export function parseInterval(value: string | string[] | undefined): Interval {
  return typeof value === "string" && (INTERVALS as readonly string[]).includes(value) ? (value as Interval) : "1d";
}

export type TrackOutcome =
  | "TRACKED"
  | "QUEUED"
  | "ALREADY_TRACKED"
  | "UNKNOWN_STOCK"
  | "TOTAL_LIMIT_REACHED"
  | "DAILY_LIMIT_REACHED"
  | "FAILED";

/** Starts broker tracking for a stock (capped by the backend); its first month is fetched in the background. */
export async function trackStock(ticker: string): Promise<TrackOutcome> {
  const response = await fetch(`${API_BASE_URL}/api/stocks/${encodeURIComponent(ticker)}/track`, { method: "POST" });
  if (!response.ok) {
    throw new Error(`Track request failed: ${response.status}`);
  }
  const result: { outcome: TrackOutcome; loadingNow: boolean } = await response.json();
  // Tracked, but today's free quota is spent: the data loads after the 00:00 WIB reset instead.
  return result.outcome === "TRACKED" && !result.loadingNow ? "QUEUED" : result.outcome;
}

/** Real-time quote from IDX's running trade (via IDX Edge), during market hours only. */
export type LiveQuote = {
  code: string;
  lastPrice: number | null;
  change: number | null;
  changePercent: number | null;
  lot: number | null; // lots traded today
  value: number | null; // IDR traded today
  frequency: number | null;
  vwap: number | null;
  dataTime: string | null; // "HH:mm:ss" WIB
  marketState: string | null;
  marketLabel: string | null; // provider's session label, Indonesian
  noTradeToday: boolean | null;
};

export type LiveStatus = "LIVE" | "MARKET_CLOSED" | "BUDGET_USED" | "UNAVAILABLE";

export type LiveResult = {
  status: LiveStatus;
  quote: LiveQuote | null;
  fetchedAt: string | null;
  budgetLeft: number;
};

export async function getLivePrice(ticker: string): Promise<LiveResult> {
  const response = await fetch(`${API_BASE_URL}/api/stocks/${encodeURIComponent(ticker)}/live`);
  if (!response.ok) {
    throw new Error(`Live price request failed: ${response.status}`);
  }
  return response.json();
}

export const RADAR_WINDOWS = [5, 10, 20] as const;
export type RadarWindow = (typeof RADAR_WINDOWS)[number];

export type RadarRow = {
  ticker: string;
  name: string;
  score: number;
  status: FlowStatus;
  brokerDays: number;
  accumulationStreak: number; // +n days of top-5 net buying in a row, -n of net selling
  avgConcentration: number;
  foreignNetValue: number | null;
  foreignDays: number;
  streakBroker: { code: string; name: string | null; days: number; netValue: number; averageBuyPrice: number | null } | null;
  buyersAveragePrice: number | null;
  lastPrice: number | null;
  exitGap: number | null; // last price / top buyers' average cost - 1
  return5d: number | null;
  aboveRisingMa20: boolean;
  reasons: Reason[];
  excludedBecause: Reason | null;
};

export type PickOutcome = {
  pickDate: string;
  ticker: string;
  bucket: string;
  claimedWinRate: number | null; // provider's claim, percent
  claimedPotential: number | null;
  entryClose: number | null;
  returns: (number | null)[]; // after 1, 3, 5 trading days
  excess: (number | null)[]; // same, minus IHSG
};

export type OutcomeSummary = {
  days: number;
  picks: number;
  hitRate: number | null;
  averageReturn: number | null;
  averageExcess: number | null;
};

export type Radar = {
  generatedAt: string;
  brokerDataDate: string | null;
  windowDays: RadarWindow;
  trackedStocks: number;
  rows: RadarRow[];
  screener: { picks: PickOutcome[]; summary: OutcomeSummary[]; averageClaimedWinRate: number | null };
};

export async function getRadar(window: RadarWindow): Promise<Radar> {
  const response = await fetch(`${API_BASE_URL}/api/watchlist/radar?window=${window}`);
  if (!response.ok) {
    throw new Error(`Radar request failed: ${response.status}`);
  }
  return response.json();
}

export async function getMarketOverview(): Promise<MarketOverview> {
  const response = await fetch(`${API_BASE_URL}/api/market/overview`);
  if (!response.ok) {
    throw new Error(`Market overview request failed: ${response.status}`);
  }
  return response.json();
}

/** Returns null when the backend does not know the ticker. */
export async function getStock(ticker: string): Promise<StockDetail | null> {
  const response = await fetch(`${API_BASE_URL}/api/stocks/${encodeURIComponent(ticker)}`);
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Stock request failed: ${response.status}`);
  }
  return response.json();
}

/** Returns null when the backend does not know the ticker. */
export async function getPriceHistory(
  ticker: string,
  range: PriceRange,
): Promise<PriceHistory | null> {
  const response = await fetch(
    `${API_BASE_URL}/api/stocks/${encodeURIComponent(ticker)}/prices?range=${range}`,
  );
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Price history request failed: ${response.status}`);
  }
  return response.json();
}

/** Returns null when the backend does not know the ticker. */
export async function getBrokerSummary(
  ticker: string,
  range: PriceRange,
): Promise<BrokerSummary | null> {
  const response = await fetch(
    `${API_BASE_URL}/api/stocks/${encodeURIComponent(ticker)}/brokers?range=${range}`,
  );
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Broker summary request failed: ${response.status}`);
  }
  return response.json();
}
