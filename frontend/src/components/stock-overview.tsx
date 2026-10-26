import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import {
  getBrokerSummary,
  getPriceHistory,
  getStock,
  PRICE_RANGES,
  type PriceHistory,
  type PriceRange,
  type StockDetail,
} from "@/lib/api";
import {
  changeColor,
  formatChange,
  formatCompact,
  formatJakartaTime,
  formatPercent,
  formatPrice,
  formatTradingDate,
} from "@/lib/format";
import { BrokerActivity } from "./broker-activity";
import { DemoBanner } from "./demo-banner";
import { PriceChart } from "./price-chart";

export async function StockOverview({ ticker, range }: { ticker: string; range: PriceRange }) {
  // Broker data is optional for this page: if it fails, show prices without it.
  const brokerRequest = getBrokerSummary(ticker, range).catch((error) => {
    unstable_rethrow(error);
    console.error(error);
    return undefined;
  });

  let stock: StockDetail | null;
  let history: PriceHistory | null;
  try {
    // Both requests start together instead of one after the other.
    [stock, history] = await Promise.all([getStock(ticker), getPriceHistory(ticker, range)]);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-red-900 bg-red-950/40 p-6 text-red-300">
        Cannot load data for {ticker}. Check that the backend is running on port 8080.
      </div>
    );
  }

  if (!stock || !history) notFound();
  const { quote } = stock;
  const brokers = await brokerRequest;

  return (
    <div className="space-y-6">
      {stock.demo && <DemoBanner notice={stock.notice} />}

      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-mono text-3xl font-bold">{quote.ticker}</h1>
          <p className="text-slate-300">{quote.name}</p>
          <p className="text-sm text-slate-500">{quote.sector}</p>
        </div>
        <div className="text-right">
          <p className="text-3xl font-bold tabular-nums">{formatPrice(quote.lastPrice)}</p>
          <p className={`tabular-nums ${changeColor(quote.change)}`}>
            {formatChange(quote.change)} ({formatPercent(quote.changePercent)})
          </p>
          <p className="text-xs text-slate-500">
            Vol {formatCompact(quote.volume)} · Est. value Rp{formatCompact(quote.value)}
          </p>
          {quote.tradeDate && (
            <p className="text-xs text-slate-500">Close of {formatTradingDate(quote.tradeDate)}</p>
          )}
        </div>
      </header>

      <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
            Daily price
          </h2>
          <nav className="flex gap-1" aria-label="Price range">
            {PRICE_RANGES.map((r) => (
              <Link
                key={r}
                href={`/stocks/${quote.ticker}?range=${r}`}
                scroll={false}
                aria-current={r === range ? "page" : undefined}
                className={`rounded-md px-3 py-1 text-sm ${
                  r === range
                    ? "bg-slate-700 font-semibold text-white"
                    : "text-slate-400 hover:bg-slate-800 hover:text-slate-200"
                }`}
              >
                {r}
              </Link>
            ))}
          </nav>
        </div>
        <PriceChart bars={history.bars} flows={brokers?.daily} />
      </section>

      {brokers ? (
        <BrokerActivity summary={brokers} />
      ) : (
        <p className="rounded-xl border border-slate-800 p-4 text-sm text-slate-500">
          Broker activity is unavailable right now.
        </p>
      )}

      <details className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
        <summary className="cursor-pointer text-sm font-semibold text-slate-300">
          Price table ({history.bars.length} trading days)
        </summary>
        <div className="mt-3 max-h-96 overflow-auto">
          <table className="w-full text-sm tabular-nums">
            <thead className="sticky top-0 bg-slate-900 text-left text-xs text-slate-500">
              <tr>
                <th className="py-1 font-medium">Date</th>
                <th className="py-1 text-right font-medium">Open</th>
                <th className="py-1 text-right font-medium">High</th>
                <th className="py-1 text-right font-medium">Low</th>
                <th className="py-1 text-right font-medium">Close</th>
                <th className="py-1 text-right font-medium">Volume</th>
              </tr>
            </thead>
            <tbody>
              {history.bars.toReversed().map((bar) => (
                <tr key={bar.date} className="border-t border-slate-800">
                  <td className="py-1.5">{formatTradingDate(bar.date)}</td>
                  <td className="py-1.5 text-right">{formatPrice(bar.open)}</td>
                  <td className="py-1.5 text-right">{formatPrice(bar.high)}</td>
                  <td className="py-1.5 text-right">{formatPrice(bar.low)}</td>
                  <td className="py-1.5 text-right">{formatPrice(bar.close)}</td>
                  <td className="py-1.5 text-right">{formatCompact(bar.volume)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      <p className="text-xs text-slate-500">
        Prices: {history.dataSource}
        {history.dataDate && <> · End-of-day data through {formatTradingDate(history.dataDate)}</>}
        {brokers && <> · Broker data: {brokers.dataSource}</>}
        {" · "}Generated {formatJakartaTime(history.generatedAt)}
        {history.demo &&
          " · Demo history is invented, skips weekends but not IDX holidays, and is not market data."}
        {!history.demo && " · Prices are not adjusted for stock splits or dividends."}
      </p>
    </div>
  );
}
