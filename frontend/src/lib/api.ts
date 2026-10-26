// Server-only: API_BASE_URL has no NEXT_PUBLIC_ prefix, so it never reaches the browser.
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

// Mirrors the Java records in backend/.../market. Nullable means "not available", not zero.
export type StockQuote = {
  ticker: string;
  name: string;
  sector: string;
  lastPrice: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  volume: number | null;
  value: number | null;
};

export type IndexSnapshot = {
  code: string;
  name: string;
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
  compositeIndex: IndexSnapshot;
  advancers: number;
  decliners: number;
  unchanged: number;
  topGainers: StockQuote[];
  topLosers: StockQuote[];
  mostActive: StockQuote[];
};

type DataLabel = {
  demo: boolean;
  dataSource: string;
  notice: string | null;
  generatedAt: string;
};

export type StockDetail = DataLabel & { quote: StockQuote };

export type PriceBar = {
  date: string; // YYYY-MM-DD trading day
  open: number;
  high: number;
  low: number;
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

export type BrokerType = "FOREIGN" | "DOMESTIC";

// Volumes in shares (1 lot = 100 shares), values in IDR.
export type BrokerPosition = {
  brokerCode: string;
  brokerType: BrokerType;
  buyVolume: number;
  buyValue: number;
  buyAveragePrice: number | null;
  sellVolume: number;
  sellValue: number;
  sellAveragePrice: number | null;
  netVolume: number;
  netValue: number;
};

export type DailyBrokerFlow = {
  date: string;
  totalVolume: number;
  top5NetBuyVolume: number;
  top5NetSellVolume: number;
  concentration: number; // fraction of total volume, e.g. 0.06 = 6%
  buyerCount: number;
  sellerCount: number;
  foreignNetValue: number;
  cumulativeForeignNetValue: number;
};

export type FlowStatus = "ACCUMULATION" | "NEUTRAL" | "DISTRIBUTION";

export type BrokerSummary = DataLabel & {
  ticker: string;
  range: PriceRange;
  from: string;
  to: string;
  lastPrice: number | null;
  assessment: { status: FlowStatus; reasons: string[] };
  topBuyers: BrokerPosition[];
  topSellers: BrokerPosition[];
  daily: DailyBrokerFlow[];
};

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
