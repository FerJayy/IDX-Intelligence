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
import { changeColor } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import { BrokerActivity } from "./broker-activity";
import { DemoBanner } from "./demo-banner";
import { PriceChart } from "./price-chart";

export async function StockOverview({ ticker, range }: { ticker: string; range: PriceRange }) {
  const { locale, t, f } = await getI18n();

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
      <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">
        {t.stock.cannotLoad(ticker)}
      </div>
    );
  }

  if (!stock || !history) notFound();
  const { quote } = stock;
  const brokers = await brokerRequest;

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

      <section className="rounded-xl border border-line bg-card p-4">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t.stock.dailyPrice}</h2>
          <nav className="flex gap-1" aria-label={t.stock.priceRange}>
            {PRICE_RANGES.map((r) => (
              <Link
                key={r}
                href={`/${locale}/stocks/${quote.ticker}?range=${r}`}
                scroll={false}
                aria-current={r === range ? "page" : undefined}
                className={`rounded-md px-3 py-1 text-sm ${
                  r === range ? "bg-chip font-semibold text-fg" : "text-muted hover:bg-chip hover:text-fg"
                }`}
              >
                {r}
              </Link>
            ))}
          </nav>
        </div>
        <PriceChart locale={locale} labels={t.chart} bars={history.bars} flows={brokers?.daily} />
      </section>

      {brokers ? (
        <BrokerActivity summary={brokers} />
      ) : (
        <p className="rounded-xl border border-line p-4 text-sm text-faint">{t.stock.brokerUnavailable}</p>
      )}

      <details className="rounded-xl border border-line bg-card p-4">
        <summary className="cursor-pointer text-sm font-semibold">{t.stock.priceTable(history.bars.length)}</summary>
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
              {history.bars.toReversed().map((bar) => (
                <tr key={bar.date} className="border-t border-line">
                  <td className="py-1.5">{f.tradingDate(bar.date)}</td>
                  <td className="py-1.5 text-right">{f.price(bar.open)}</td>
                  <td className="py-1.5 text-right">{f.price(bar.high)}</td>
                  <td className="py-1.5 text-right">{f.price(bar.low)}</td>
                  <td className="py-1.5 text-right">{f.price(bar.close)}</td>
                  <td className="py-1.5 text-right">{f.compact(bar.volume)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      <p className="text-xs text-faint">
        {t.stock.prices}: {history.dataSource}
        {history.dataDate && <> · {t.common.endOfDayThrough(f.tradingDate(history.dataDate))}</>}
        {brokers && <> · {t.stock.brokerData}: {brokers.dataSource}</>}
        {" · "}
        {t.common.generated} {f.jakartaTime(history.generatedAt)}
        {" · "}
        {history.demo ? t.stock.demoHistory : t.stock.notAdjusted}
      </p>
    </div>
  );
}
