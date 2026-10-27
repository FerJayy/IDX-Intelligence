import { notFound, unstable_rethrow } from "next/navigation";
import {
  getBrokerSummary,
  getStock,
  getStockCandles,
  PRICE_RANGES,
  type CandleSeries,
  type Interval,
  type PriceRange,
  type StockDetail,
  type TrackOutcome,
} from "@/lib/api";
import { loadBrokerData } from "@/lib/actions";
import { changeColor } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import { BrokerActivity } from "./broker-activity";
import { ChartControls } from "./chart-controls";
import { DemoBanner } from "./demo-banner";
import { PriceChart } from "./price-chart";

export async function StockOverview({
  ticker,
  interval,
  range,
  tracking,
}: {
  ticker: string;
  interval: Interval;
  range?: string;
  /** Outcome of a "Load broker data" click, from the redirect. */
  tracking?: TrackOutcome;
}) {
  const { locale, t, f } = await getI18n();

  // Broker data is daily: on 1D it follows the chart's range, otherwise it shows 3 months.
  const brokerRange: PriceRange =
    interval === "1d" && (PRICE_RANGES as readonly string[]).includes(range ?? "") ? (range as PriceRange) : "3M";
  // Broker data is optional for this page: if it fails, show prices without it.
  const brokerRequest = getBrokerSummary(ticker, brokerRange).catch((error) => {
    unstable_rethrow(error);
    console.error(error);
    return undefined;
  });

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
  const brokers = await brokerRequest;
  const hrefFor = (i: Interval, r?: string) =>
    `/${locale}/stocks/${quote.ticker}?tf=${i}${r ? `&range=${r}` : ""}`;

  return (
    <div className="space-y-6">
      {stock.demo && <DemoBanner notice={t.common.demoNotice} />}

      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-mono text-3xl font-bold">{quote.ticker}</h1>
          <p className="text-fg">{quote.name}</p>
          {quote.sector && <p className="text-sm text-faint">{quote.sector}</p>}
        </div>
        <div className="text-right">
          <p className="text-3xl font-bold tabular-nums">{f.price(quote.lastPrice)}</p>
          <p className={`tabular-nums ${changeColor(quote.change)}`}>
            {f.change(quote.change)} ({f.percent(quote.changePercent)})
          </p>
          <p className="text-xs text-faint">
            {t.stock.vol} {f.compact(quote.volume)} · {t.stock.estValue} {f.rupiah(quote.value)}
          </p>
          {quote.tradeDate && <p className="text-xs text-faint">{t.stock.closeOf(f.tradingDate(quote.tradeDate))}</p>}
        </div>
      </header>

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
      ) : brokers ? (
        <BrokerActivity summary={brokers} />
      ) : (
        <p className="rounded-xl border border-line p-4 text-sm text-faint">{t.stock.brokerUnavailable}</p>
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
