import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import {
  getIndexCandles,
  getLiveMovers,
  getMarketOverview,
  type CandleSeries,
  type LiveMover,
  type LiveMovers,
  type MarketOverview as Overview,
  type StockQuote,
} from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { DemoBanner } from "./demo-banner";
import { HscBadge } from "./hsc-badge";
import { IhsgLive } from "./ihsg-live";
import { Sparkline } from "./sparkline";

export const MOVER_TABS = ["gainers", "losers", "active"] as const;
export type MoverTab = (typeof MOVER_TABS)[number];

// Server Component: fetches on the server at request time, streamed in via <Suspense>.
export async function MarketOverview({ tab }: { tab: MoverTab }) {
  const { locale, t, f } = await getI18n();
  let overview: Overview;
  let ihsg: CandleSeries | null;
  let live: LiveMovers | null;
  try {
    [overview, ihsg, live] = await Promise.all([
      getMarketOverview(),
      // The IHSG card's trend line; if it fails, the card still shows the value.
      getIndexCandles("1d", "3M").catch((error) => {
        unstable_rethrow(error);
        return null;
      }),
      // Today's lists during the session; without them the end-of-day lists apply.
      getLiveMovers().catch((error) => {
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
            <IhsgLive
              locale={locale}
              close={index.value}
              change={index.change}
              changePercent={index.changePercent}
              closeDate={index.tradeDate}
              labels={{ live: t.live.badge, delayed: t.dashboard.ihsgDelayed }}
            />
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
          <p className="text-sm text-muted">
            {t.overview.breadth}
            {/* Breadth needs every stock's price, which only the evening import has. */}
            {live?.available && overview.dataDate && (
              <span className="ml-2 text-xs text-faint">{t.dashboard.closeOf(f.tradingDate(overview.dataDate))}</span>
            )}
          </p>
          <div className="mt-2 grid grid-cols-3 text-center">
            <Breadth href={stocksLink("change=up&sort=change&order=desc")} label={t.overview.up} count={overview.advancers} color="text-up" />
            <Breadth href={stocksLink("change=down&sort=change&order=asc")} label={t.overview.down} count={overview.decliners} color="text-down" />
            <Breadth href={stocksLink("change=flat&sort=value&order=desc")} label={t.overview.flat} count={overview.unchanged} color="text-fg" />
          </div>
          <p className="mt-3 text-center text-xs text-faint">{t.dashboard.breadthHint}</p>
        </section>
      </div>

      <Movers overview={overview} live={live?.available ? live : null} tab={tab} locale={locale} t={t} f={f} />

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
  live,
  tab,
  locale,
  t,
  f,
}: {
  overview: Overview;
  /** Today's lists while the market trades; null = show the last end-of-day lists. */
  live: LiveMovers | null;
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
  const { stocks: endOfDay, seeAll } = lists[tab];
  const liveLists: Record<MoverTab, LiveMover[]> | null = live
    ? { gainers: live.gainers, losers: live.losers, active: live.mostActive }
    : null;
  // Both kinds of rows share one shape for the table below.
  const stocks: (StockQuote | LiveMover)[] = liveLists ? liveLists[tab] : endOfDay;

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
      <p className="px-4 pt-2 text-xs">
        {live ? (
          <>
            <span className="font-semibold text-up">● {t.live.badge}</span>{" "}
            <span className="text-faint">
              {t.dashboard.liveAsOf(f.jakartaTime(live.fetchedAt!))}
              {live.marketLabel && <> · {live.marketLabel}</>}
            </span>
          </>
        ) : (
          overview.dataDate && <span className="text-faint">{t.dashboard.closeOf(f.tradingDate(overview.dataDate))}</span>
        )}
      </p>

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
                    {s.hsc && <HscBadge label={t.hsc.badge} title={t.hsc.title} />}
                    {"thin" in s && s.thin && (
                      <span title={t.dashboard.thinTitle} className="ml-2 rounded bg-warn-bg px-1.5 py-0.5 text-[10px] font-semibold text-warn-fg">
                        {t.dashboard.thin}
                      </span>
                    )}
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
      {live ? (
        <p className="border-t border-line px-4 py-2 text-xs text-faint">{t.dashboard.liveNote}</p>
      ) : (
        tab !== "active" && <p className="border-t border-line px-4 py-2 text-xs text-faint">{t.dashboard.moversNote}</p>
      )}
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
