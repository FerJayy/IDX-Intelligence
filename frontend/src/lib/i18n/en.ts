import type { Format } from "@/lib/format";

// Values sent by the backend with a reason code (see backend Reason.java).
export type ReasonValues = Record<string, unknown>;
type ReasonText = (v: ReasonValues, f: Format) => string;

const n = (v: unknown) => (typeof v === "number" ? v : Number(v));

export const en = {
  meta: {
    title: "IDX Intelligence",
    description: "Indonesian Stock Exchange research: prices, broker activity and backtested signals",
  },
  common: {
    appName: "IDX Intelligence",
    tagline: "IDX research tool — not investment advice.",
    demoNotice: "DEMO DATA — NOT LIVE MARKET DATA",
    loadingMarket: "Loading market data…",
    loadingStock: "Loading stock data…",
    backendDown: "Cannot load market data. Check that the backend is running on port 8080.",
    source: "Source",
    generated: "Generated",
    endOfDayFor: (date: string) => `End-of-day data for ${date}`,
    endOfDayThrough: (date: string) => `End-of-day data through ${date}`,
    demoFigures: "Figures are invented for development and are not market prices.",
    estValueNote: "Traded value is estimated as last price × volume. Not investment advice.",
  },
  nav: {
    overview: "Overview",
    stocks: "Stocks",
  },
  theme: {
    label: "Theme",
    light: "Light",
    dark: "Dark",
    system: "System",
  },
  language: {
    label: "Language",
    en: "English",
    id: "Bahasa Indonesia",
  },
  overview: {
    ihsg: "IDX Composite (IHSG)",
    ihsgMissing: "Not imported yet — run the price import.",
    breadth: "Market breadth",
    up: "Up",
    down: "Down",
    flat: "Flat",
    topGainers: "Top gainers",
    topLosers: "Top losers",
    mostActive: "Most active",
    ticker: "Ticker",
    last: "Last",
    change: "Change",
    estValue: "Est. value (Rp)",
    noStocks: "No stocks to show.",
  },
  stockList: {
    title: "All stocks",
    search: "Search code or name",
    searchButton: "Search",
    allSectors: "All sectors",
    sortBy: "Sort by",
    sorts: {
      marketCap: "Market cap",
      change: "Change today",
      value: "Traded value",
      volume: "Volume",
      price: "Price",
      ticker: "Code",
    },
    order: { desc: "High to low", asc: "Low to high" },
    found: (total: number) => `${total} stocks`,
    stock: "Stock",
    sector: "Sector",
    last: "Last",
    change: "Change",
    volume: "Volume",
    value: "Est. value",
    marketCap: "Market cap",
    brokerTag: "Broker data",
    brokerTagTitle: "Broker summaries are imported for this stock",
    noMatch: "No stocks match.",
    previous: "← Previous",
    next: "Next →",
    pageOf: (page: number, pages: number) => `Page ${page} of ${pages}`,
    unknownSector: "—",
  },
  stock: {
    back: "← Market overview",
    cannotLoad: (ticker: string) => `Cannot load data for ${ticker}. Check that the backend is running on port 8080.`,
    closeOf: (date: string) => `Close of ${date}`,
    vol: "Vol",
    estValue: "Est. value",
    dailyPrice: "Daily price",
    priceRange: "Price range",
    priceTable: (days: number) => `Price table (${days} trading days)`,
    date: "Date",
    open: "Open",
    high: "High",
    low: "Low",
    close: "Close",
    volume: "Volume",
    prices: "Prices",
    brokerData: "Broker data",
    demoHistory: "Demo history is invented, skips weekends but not IDX holidays, and is not market data.",
    notAdjusted: "Prices are not adjusted for stock splits or dividends.",
    brokerUnavailable: "Broker activity is unavailable right now.",
    brokerNotTracked:
      "Broker data isn't imported for this stock. To stay within the free data plan, broker summaries cover a tracked list of stocks; prices cover all of them.",
  },
  chart: {
    noHistory: "No price history available.",
    date: "Date",
    open: "O",
    high: "H",
    low: "L",
    close: "C",
    change: "Chg",
    volume: "Vol",
    concentration: "Top-5 conc.",
    buyersSellers: "Buyers/Sellers",
    foreignNet: "Foreign net",
    concentrationSeries: "Top-5 conc.",
    foreignSeries: "Foreign net (cum.)",
  },
  broker: {
    title: (range: string) => `Broker activity · ${range}`,
    demoTag: "DEMO DATA",
    coverage: (brokerDays: number, tradingDays: number) =>
      `Broker data covers ${brokerDays} of ${tradingDays} trading days in this range. History is still being imported (newest days first) and fills in a little more each day.`,
    status: { ACCUMULATION: "Accumulation", NEUTRAL: "Neutral", DISTRIBUTION: "Distribution" },
    disclaimer:
      "Rule-based description of past broker flow, not a forecast or a recommendation to buy or sell. The rules have not been backtested yet.",
    topBuyers: "Top net buyers",
    topSellers: "Top net sellers",
    broker: "Broker",
    netLot: "Net lot",
    netValue: "Net value",
    avgBuy: "Avg buy",
    avgSell: "Avg sell",
    none: "None in this period.",
    howToRead: "How to read this",
    terms: [
      {
        name: "Top-5 concentration",
        text: "Each day: lots net bought by the 5 biggest net buyers, minus lots net sold by the 5 biggest net sellers, as a share of that day's volume. Above zero means buying is concentrated in a few brokers while selling is spread across many — a common sign of accumulation. Below zero is the reverse.",
      },
      {
        name: "Buyers / sellers",
        text: "How many brokers ended the day as net buyers vs net sellers. Fewer buyers than sellers means the buying sits in fewer hands.",
      },
      {
        name: "Foreign net",
        text: "Net value bought by foreign investors, across all brokers they traded through.",
      },
      {
        name: "Average price",
        text: "Value divided by volume for that broker over the period: roughly where its position was built (buyers) or unwound (sellers).",
      },
    ],
  },
  reasons: {
    NO_BROKER_DATA: () => "No broker data for this period yet.",
    TOO_FEW_BROKER_DAYS: (v) =>
      `Only ${v.days} trading day${n(v.days) === 1 ? "" : "s"} of broker data so far; an assessment needs at least ${v.minDays}.`,
    CONCENTRATION_ACCUMULATION: (v, f) =>
      `The top 5 net buyers absorbed more than the top 5 net sellers released: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume (accumulation above +${f.fraction(n(v.threshold))}).`,
    CONCENTRATION_DISTRIBUTION: (v, f) =>
      `The top 5 net sellers released more than the top 5 net buyers absorbed: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume (distribution below -${f.fraction(n(v.threshold))}).`,
    CONCENTRATION_BALANCED: (v, f) =>
      `The top 5 net buyers and sellers were roughly balanced: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume.`,
    BREADTH_BUYING_CONCENTRATED: (v, f) =>
      `Fewer brokers were net buying than net selling on ${f.fraction(n(v.share))} of days — buying concentrated in fewer hands.`,
    BREADTH_SELLING_CONCENTRATED: (v, f) =>
      `Fewer brokers were net buying than net selling on only ${f.fraction(n(v.share))} of days — selling concentrated in fewer hands.`,
    BREADTH_NEUTRAL: (v, f) => `Fewer brokers were net buying than net selling on ${f.fraction(n(v.share))} of days.`,
    FOREIGN_UNAVAILABLE: () => "Foreign investor flow is not available for this period yet.",
    FOREIGN_NET_BUY: (v, f) =>
      `Foreign investors net bought ${f.rupiah(n(v.value))} over the period${n(v.daysWithData) < n(v.days) ? ` (${v.daysWithData} of ${v.days} days with foreign data)` : ""}.`,
    FOREIGN_NET_SELL: (v, f) =>
      `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the period${n(v.daysWithData) < n(v.days) ? ` (${v.daysWithData} of ${v.days} days with foreign data)` : ""}.`,
    TOP_BUYERS_AVG_PRICE: (v, f) =>
      `The top 5 buyers' average buy price is Rp${f.price(n(v.avgPrice))}; the last price is ${f.fraction(Math.abs(n(v.gap)))} ${n(v.gap) >= 0 ? "above" : "below"} it.`,
    BREAKOUT_20D: (v, f) => `Closed above its 20-day high (${f.price(n(v.level))})`,
    VOLUME_SPIKE: (v, f) => `Volume ${f.decimal1(n(v.ratio))}× its 20-day average`,
    CLOSE_NEAR_HIGH: () => "Closed near the day's high",
    UP_TODAY: (v, f) => `Up ${f.signedFraction(n(v.change))} today`,
    ABOVE_RISING_MA20: (v, f) => `Above a rising 20-day average (${f.price(n(v.ma20))})`,
    BROKERS_NET_BUYING: (v, f) =>
      `Top-5 brokers net buying: avg ${f.signedFraction(n(v.avgConcentration))} of volume over the last ${v.days} days`,
    BROKERS_NET_SELLING: (v, f) =>
      `Top-5 brokers net selling: avg ${f.signedFraction(n(v.avgConcentration))} of volume over the last ${v.days} days`,
    FOREIGN_NET_BUY_RECENT: (v, f) => `Foreign investors net bought ${f.rupiah(n(v.value))} over the last ${v.days} days`,
    FOREIGN_NET_SELL_RECENT: (v, f) =>
      `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the last ${v.days} days`,
    LOW_LIQUIDITY: (v, f) =>
      `Low liquidity: averages ${f.rupiah(n(v.avgValue))} traded per day (minimum ${f.rupiah(n(v.minValue))})`,
    NEAR_LIMIT_UP: (v, f) => `Up ${f.signedFraction(n(v.change))} today, likely at the daily limit: hard to buy at the open`,
    CHASING: (v, f) => `Already up ${f.signedFraction(n(v.change5d))} in 5 days: high risk of chasing`,
  } satisfies Record<string, ReasonText>,
};

export type Dictionary = typeof en;
