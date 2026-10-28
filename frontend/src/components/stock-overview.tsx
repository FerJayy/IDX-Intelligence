import { notFound, unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import {
  getBrokerSummary,
  type BrokerPeriod,
  getStock,
  getStockCandles,
  BROKER_PERIODS,
  PRICE_RANGES,
  type CandleSeries,
  type Interval,
  type PriceRange,
  type StockDetail,
  type TrackOutcome,
} from "@/lib/api";
import { loadBrokerData } from "@/lib/actions";

import { getI18n } from "@/lib/i18n";
import { readWatchlist } from "@/lib/watchlist";
import { BrokerActivity } from "./broker-activity";
import { ChartControls } from "./chart-controls";
import { DemoBanner } from "./demo-banner";
import { HeaderPrice } from "./header-price";
import { HscBadge } from "./hsc-badge";
import { InsiderBox } from "./insider-box";
import { LivePrice } from "./live-price";
import { PriceChart } from "./price-chart";
import { StockSummaryCard } from "./stock-summary";
import { WatchButton } from "./watch-button";

export async function StockOverview({
  ticker,
  interval,
  range,
  tracking,
  brokerPeriod = "1M",
}: {
  ticker: string;
  interval: Interval;
  range?: string;
  /** Outcome of a "Load broker data" click, from the redirect. */
  tracking?: TrackOutcome;
  /** Period of the broker section (?broker=); independent of the chart's range. */
  brokerPeriod?: BrokerPeriod;
}) {
  const { locale, t, f } = await getI18n();

  // The chart's broker panels follow the chart's range (daily candles only); the broker section has
  // its own period selector, so its totals never silently change with the chart.
  const brokerRange: PriceRange =
    interval === "1d" && (PRICE_RANGES as readonly string[]).includes(range ?? "") ? (range as PriceRange) : "3M";
  // Broker data is optional for this page: if it fails, show prices without it.
  const loadBrokers = (period: PriceRange | BrokerPeriod) =>
    getBrokerSummary(ticker, period).catch((error) => {
      unstable_rethrow(error);
      console.error(error);
      return undefined;
    });
  const brokerRequest = loadBrokers(brokerRange);
  const sectionRequest = brokerPeriod === brokerRange ? brokerRequest : loadBrokers(brokerPeriod);

  let stock: StockDetail | null;
  let chart: CandleSeries | null;
  try {
    // Both requests start together instead of one after the other.
    [stock, chart] = await Promise.all([getStock(ticker), getStockCandles(ticker, interval, range)]);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">
        {t.stock.cannotLoad(ticker)}
      </div>
    );
  }

  if (!stock || !chart) notFound();
  const { quote } = stock;
  const [brokers, section, watchlist] = await Promise.all([brokerRequest, sectionRequest, readWatchlist()]);
  const brokerParam = brokerPeriod === "1M" ? "" : `&broker=${brokerPeriod}`;
  const hrefFor = (i: Interval, r?: string) =>
    `/${locale}/stocks/${quote.ticker}?tf=${i}${r ? `&range=${r}` : ""}${brokerParam}`;
  const periodLinks = BROKER_PERIODS.map((p) => ({
    code: p,
    label: t.broker.periods[p],
    href: `/${locale}/stocks/${quote.ticker}?tf=${interval}${range ? `&range=${range}` : ""}${p === "1M" ? "" : `&broker=${p}`}`,
    active: p === brokerPeriod,
  }));

  return (
    <div className="space-y-6">
      {stock.demo && <DemoBanner notice={t.common.demoNotice} />}

      {/* Name left, price right, also on phones (no wrapping: the name gives way instead). */}
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="font-mono text-3xl font-bold">
            {quote.ticker}
            {quote.hsc && <HscBadge label={t.hsc.badge} title={t.hsc.title} />}
          </h1>
          <p className="text-fg">{quote.name}</p>
          {quote.sector && <p className="text-sm text-faint">{quote.sector}</p>}
          <div className="mt-2">
            <WatchButton ticker={quote.ticker} watched={watchlist.includes(quote.ticker)} labels={t.watchlist.button} />
          </div>
        </div>
        <HeaderPrice
          ticker={stock.demo ? null : quote.ticker}
          locale={locale}
          close={quote.lastPrice}
          change={quote.change}
          changePercent={quote.changePercent}
          details={[
            `${t.stock.vol} ${f.compact(quote.volume)} · ${t.stock.estValue} ${f.rupiah(quote.value)}`,
            ...(quote.tradeDate ? [t.stock.closeOf(f.tradingDate(quote.tradeDate))] : []),
          ]}
          labels={{ live: t.live.badge, updated: t.live.updated, volume: t.live.lots, value: t.live.value, closedToday: t.live.closedToday }}
        />
      </header>

      {quote.hsc && (
        <p className="rounded-lg border border-warn-line bg-warn-bg px-4 py-2 text-sm text-warn-fg">{t.hsc.note}</p>
      )}

      {!stock.demo && <LivePrice ticker={quote.ticker} locale={locale} labels={t.live} />}

      {!stock.demo && (
        <Suspense fallback={<p className="rounded-xl border border-line p-4 text-sm text-faint">{t.common.loadingMarket}</p>}>
          <StockSummaryCard ticker={quote.ticker} />
        </Suspense>
      )}

      <section className="space-y-4 rounded-xl border border-line bg-card p-4">
        <ChartControls
          interval={interval}
          range={chart.range}
          ranges={chart.ranges}
          hrefFor={hrefFor}
          labels={t.chartControls}
        />
        <PriceChart
          locale={locale}
          labels={t.chart}
          candles={chart.candles}
          intraday={chart.intraday}
          flows={brokers?.tracked && interval === "1d" ? brokers.daily : undefined}
          live={stock.demo ? undefined : { symbol: quote.ticker, interval, range: chart.range, stock: true }}
        />
        <ChartNotes chart={chart} interval={interval} brokerTracked={!!brokers?.tracked} t={t} />
      </section>

      {tracking && (
        <p className="rounded-lg border border-info-line bg-info-bg px-4 py-2 text-sm text-info-fg">
          {t.stock.tracking[tracking]}
        </p>
      )}

      {brokers && !brokers.tracked && !brokers.demo ? (
        <form action={loadBrokerData} className="flex flex-wrap items-center gap-3 rounded-xl border border-line p-4">
          <input type="hidden" name="ticker" value={quote.ticker} />
          <input type="hidden" name="lang" value={locale} />
          <p className="min-w-64 flex-1 text-sm text-muted">
            {t.stock.brokerNotTracked} {t.stock.loadBrokerHint}
          </p>
          <button type="submit" className="rounded-md bg-chip px-4 py-2 text-sm font-semibold hover:bg-line">
            {t.stock.loadBroker}
          </button>
        </form>
      ) : section ? (
        <BrokerActivity summary={section} periods={periodLinks} />
      ) : (
        <p className="rounded-xl border border-line p-4 text-sm text-faint">{t.stock.brokerUnavailable}</p>
      )}

      {!stock.demo && (
        <Suspense fallback={<p className="rounded-xl border border-line p-4 text-sm text-faint">{t.common.loadingMarket}</p>}>
          <InsiderBox ticker={quote.ticker} />
        </Suspense>
      )}

      <details className="rounded-xl border border-line bg-card p-4">
        <summary className="cursor-pointer text-sm font-semibold">{t.stock.priceTable(chart.candles.length)}</summary>
        <div className="mt-3 max-h-96 overflow-auto">
          <table className="w-full text-sm tabular-nums">
            <thead className="sticky top-0 bg-card-strong text-left text-xs text-faint">
              <tr>
                <th className="py-1 font-medium">{t.stock.date}</th>
                <th className="py-1 text-right font-medium">{t.stock.open}</th>
                <th className="py-1 text-right font-medium">{t.stock.high}</th>
                <th className="py-1 text-right font-medium">{t.stock.low}</th>
                <th className="py-1 text-right font-medium">{t.stock.close}</th>
                <th className="py-1 text-right font-medium">{t.stock.volume}</th>
              </tr>
            </thead>
            <tbody>
              {chart.candles.toReversed().map((c) => (
                <tr key={c.time} className="border-t border-line">
                  <td className="py-1.5">{chart.intraday ? f.intradayTime(c.time) : f.candleDate(c.time)}</td>
                  <td className="py-1.5 text-right">{f.price(c.open)}</td>
                  <td className="py-1.5 text-right">{f.price(c.high)}</td>
                  <td className="py-1.5 text-right">{f.price(c.low)}</td>
                  <td className="py-1.5 text-right">{f.price(c.close)}</td>
                  <td className="py-1.5 text-right">{f.compact(c.volume)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      <p className="text-xs text-faint">
        {t.stock.prices}: {chart.dataSource}
        {stock.dataDate && <> · {t.common.endOfDayThrough(f.tradingDate(stock.dataDate))}</>}
        {brokers?.tracked && <> · {t.stock.brokerData}: {brokers.dataSource}</>}
        {" · "}
        {t.common.generated} {f.jakartaTime(chart.generatedAt)}
        {stock.demo && <> · {t.stock.demoHistory}</>}
      </p>
    </div>
  );
}

function ChartNotes({
  chart,
  interval,
  brokerTracked,
  t,
}: {
  chart: CandleSeries;
  interval: Interval;
  brokerTracked: boolean;
  t: Awaited<ReturnType<typeof getI18n>>["t"];
}) {
  const notes = [
    chart.intraday && t.chartControls.intradayNote,
    chart.adjusted ? t.chartControls.adjustedNote : !chart.intraday && t.stock.notAdjusted,
    brokerTracked && interval !== "1d" && t.chartControls.brokerDailyOnly,
  ].filter(Boolean);
  return notes.length ? <p className="text-xs text-faint">{notes.join(" ")}</p> : null;
}
