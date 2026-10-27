import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import {
  getIndexCandles,
  getMarketOverview,
  type CandleSeries,
  type MarketOverview as Overview,
  type StockQuote,
} from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { DemoBanner } from "./demo-banner";
import { Sparkline } from "./sparkline";

export const MOVER_TABS = ["gainers", "losers", "active"] as const;
export type MoverTab = (typeof MOVER_TABS)[number];

// Server Component: fetches on the server at request time, streamed in via <Suspense>.
export async function MarketOverview({ tab }: { tab: MoverTab }) {
  const { locale, t, f } = await getI18n();
  let overview: Overview;
  let ihsg: CandleSeries | null;
  try {
    [overview, ihsg] = await Promise.all([
      getMarketOverview(),
      // The IHSG card's trend line; if it fails, the card still shows the value.
      getIndexCandles("1d", "3M").catch((error) => {
        unstable_rethrow(error);
        return null;
      }),
    ]);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>
    );
  }

  const index = overview.compositeIndex;
  const stocksLink = (query: string) => `/${locale}/stocks?${query}`;

  return (
    <div className="space-y-6">
      {overview.demo && <DemoBanner notice={t.common.demoNotice} />}

      <div className="grid gap-4 lg:grid-cols-3">
        <Link
          href={`/${locale}/ihsg`}
          className="group rounded-xl border border-line bg-card p-5 transition-colors hover:border-accent lg:col-span-2"
        >
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="text-sm text-muted">{t.overview.ihsg}</p>
            <span className="text-xs text-accent group-hover:underline">{t.dashboard.openIhsg}</span>
          </div>
          {index ? (
            <div className="mt-2 flex flex-wrap items-baseline gap-x-3">
              <p className="text-3xl font-bold tabular-nums">{f.index(index.value)}</p>
              <p className={`tabular-nums ${changeColor(index.change)}`}>
                {f.change(index.change, 2)} ({f.percent(index.changePercent)})
              </p>
            </div>
          ) : (
            <p className="mt-2 text-sm text-faint">{t.overview.ihsgMissing}</p>
          )}
          {ihsg && ihsg.candles.length > 1 && (
            <div className="mt-3">
              <Sparkline values={ihsg.candles.map((c) => c.close)} width={600} height={72} fluid />
              <p className="mt-1 text-xs text-faint">3M</p>
            </div>
          )}
        </Link>

        <section className="rounded-xl border border-line bg-card p-5">
          <p className="text-sm text-muted">{t.overview.breadth}</p>
          <div className="mt-2 grid grid-cols-3 text-center">
            <Breadth href={stocksLink("change=up&sort=change&order=desc")} label={t.overview.up} count={overview.advancers} color="text-up" />
            <Breadth href={stocksLink("change=down&sort=change&order=asc")} label={t.overview.down} count={overview.decliners} color="text-down" />
            <Breadth href={stocksLink("change=flat&sort=value&order=desc")} label={t.overview.flat} count={overview.unchanged} color="text-fg" />
          </div>
          <p className="mt-3 text-center text-xs text-faint">{t.dashboard.breadthHint}</p>
        </section>
      </div>

      <Movers overview={overview} tab={tab} locale={locale} t={t} f={f} />

      <p className="text-xs text-faint">
        {t.common.source}: {overview.dataSource}
        {overview.dataDate && <> · {t.common.endOfDayFor(f.tradingDate(overview.dataDate))}</>}
        {" · "}
        {t.common.generated} {f.jakartaTime(overview.generatedAt)}
        {" · "}
        {overview.demo ? t.common.demoFigures : t.common.estValueNote}
      </p>
    </div>
  );
}

function Movers({
  overview,
  tab,
  locale,
  t,
  f,
}: {
  overview: Overview;
  tab: MoverTab;
  locale: Locale;
  t: Dictionary;
  f: Format;
}) {
  const lists: Record<MoverTab, { stocks: StockQuote[]; seeAll: string }> = {
    gainers: { stocks: overview.topGainers, seeAll: "sort=change&order=desc" },
    losers: { stocks: overview.topLosers, seeAll: "sort=change&order=asc" },
    active: { stocks: overview.mostActive, seeAll: "sort=value&order=desc" },
  };
  const { stocks, seeAll } = lists[tab];

  return (
    <section className="rounded-xl border border-line bg-card">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 pt-3">
        <nav className="flex gap-1" aria-label={t.overview.breadth}>
          {MOVER_TABS.map((option) => (
            <Link
              key={option}
              href={`/${locale}?movers=${option}`}
              scroll={false}
              aria-current={option === tab ? "page" : undefined}
              className={`-mb-px border-b-2 px-3 pb-2 text-sm ${
                option === tab ? "border-accent font-semibold text-fg" : "border-transparent text-muted hover:text-fg"
              }`}
            >
              {t.dashboard.tabs[option]}
            </Link>
          ))}
        </nav>
        <Link href={`/${locale}/stocks?${seeAll}`} className="pb-2 text-sm text-accent hover:underline">
          {t.dashboard.seeAll}
        </Link>
      </div>

      {stocks.length === 0 ? (
        <p className="p-4 text-sm text-faint">{t.overview.noStocks}</p>
      ) : (
        <table className="w-full text-sm tabular-nums">
          <thead className="text-left text-xs text-faint">
            <tr>
              <th className="px-4 py-2 font-medium">{t.overview.ticker}</th>
              <th className="hidden px-4 py-2 font-medium sm:table-cell">{t.dashboard.trend}</th>
              <th className="px-4 py-2 text-right font-medium">{t.overview.last}</th>
              <th className="px-4 py-2 text-right font-medium">{t.overview.change}</th>
              <th className="hidden px-4 py-2 text-right font-medium sm:table-cell">{t.overview.estValue}</th>
            </tr>
          </thead>
          <tbody>
            {stocks.map((s) => (
              <tr key={s.ticker} className="border-t border-line hover:bg-card-strong">
                <td className="max-w-56 px-4 py-2">
                  <Link href={`/${locale}/stocks/${s.ticker}`} className="group block">
                    <span className="font-mono font-semibold group-hover:text-accent group-hover:underline">{s.ticker}</span>
                    <span className="block truncate text-xs text-faint">{s.name}</span>
                  </Link>
                </td>
                <td className="hidden px-4 py-2 sm:table-cell">
                  <Sparkline values={overview.sparklines[s.ticker] ?? []} />
                </td>
                <td className="px-4 py-2 text-right">{f.price(s.lastPrice)}</td>
                <td className={`px-4 py-2 text-right ${changeColor(s.changePercent)}`}>{f.percent(s.changePercent)}</td>
                <td className="hidden px-4 py-2 text-right sm:table-cell">{f.rupiah(s.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {tab !== "active" && <p className="border-t border-line px-4 py-2 text-xs text-faint">{t.dashboard.moversNote}</p>}
    </section>
  );
}

function Breadth({ href, label, count, color }: { href: string; label: string; count: number; color: string }) {
  return (
    <Link href={href} className="rounded-lg py-1 hover:bg-card-strong">
      <p className={`text-3xl font-bold tabular-nums ${color}`}>{count}</p>
      <p className="text-xs text-faint">{label}</p>
    </Link>
  );
}
