import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { HscBadge } from "@/components/hsc-badge";
import { addToWatchlist, toggleWatch } from "@/lib/actions";
import { getRadar, getStock, type FlowStatus, type Radar, type StockQuote } from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { readWatchlist, WATCHLIST_MAX } from "@/lib/watchlist";

// Status always shows an icon and a word, never colour alone.
const STATUS: Record<FlowStatus, { icon: string; className: string }> = {
  ACCUMULATION: { icon: "▲", className: "border-up-line bg-up-bg text-up" },
  NEUTRAL: { icon: "●", className: "border-line bg-chip text-fg" },
  DISTRIBUTION: { icon: "▼", className: "border-down-line bg-down-bg text-down" },
};

export default async function WatchlistPage({ searchParams }: PageProps<"/[lang]/watchlist">) {
  const { locale, t } = await getI18n();
  // The cookie and searchParams are request-time data, read inside <Suspense> (cacheComponents).
  const message = searchParams.then((query) => {
    const ticker = typeof query.ticker === "string" ? query.ticker : "";
    if (query.problem === "unknown") return t.watchlist.unknown(ticker);
    if (query.problem === "full") return t.watchlist.full(WATCHLIST_MAX);
    return null;
  });
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold">{t.watchlist.title}</h1>
          <p className="mt-2 text-sm text-muted">{t.watchlist.intro}</p>
        </div>

        <form action={addToWatchlist} className="flex flex-wrap items-end gap-2">
          <input type="hidden" name="lang" value={locale} />
          <label className="flex flex-col gap-1 text-xs text-faint">
            {t.watchlist.addLabel}
            <input
              name="ticker"
              required
              maxLength={4}
              autoCapitalize="characters"
              autoComplete="off"
              placeholder={t.watchlist.addPlaceholder}
              className="w-32 rounded-md border border-line bg-card px-3 py-1.5 font-mono text-sm uppercase text-fg placeholder:normal-case"
            />
          </label>
          <button type="submit" className="rounded-md bg-chip px-4 py-1.5 text-sm font-semibold text-fg hover:bg-card-strong">
            {t.watchlist.add}
          </button>
        </form>

        <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>
          <Message message={message} />
          <WatchList locale={locale} />
        </Suspense>

        <p className="text-xs text-faint">{t.watchlist.note}</p>
      </div>
    </main>
  );
}

async function Message({ message }: { message: Promise<string | null> }) {
  const text = await message;
  if (!text) return null;
  return <p className="rounded-lg border border-warn-line bg-warn-bg px-4 py-2 text-sm text-warn-fg">{text}</p>;
}

async function WatchList({ locale }: { locale: Locale }) {
  const { t, f } = await getI18n();
  const tickers = await readWatchlist();
  if (tickers.length === 0) {
    return <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.watchlist.empty}</p>;
  }

  // The Radar is shared and cached by the backend, but its first load can take a while:
  // prices render first and each stock's broker line streams in when it arrives.
  const radar: Promise<Radar | null> = getRadar(10).catch((error) => {
    unstable_rethrow(error);
    console.error(error);
    return null;
  });
  let quotes: (StockQuote | null)[];
  try {
    quotes = (await Promise.all(tickers.map((ticker) => getStock(ticker)))).map((detail) => detail?.quote ?? null);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>;
  }

  return (
    <section>
      <p className="mb-2 text-xs text-faint">{t.watchlist.count(tickers.length, WATCHLIST_MAX)}</p>
      <ul className="space-y-2">
        {tickers.map((ticker, i) => {
          const quote = quotes[i];
          return (
            <li key={ticker} className="rounded-xl border border-line bg-card p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <Link href={`/${locale}/stocks/${ticker}`} className="group min-w-0">
                  <span className="font-mono text-lg font-bold group-hover:text-accent group-hover:underline">{ticker}</span>
                  {quote?.hsc && <HscBadge label={t.hsc.badge} title={t.hsc.title} />}
                  {quote && <span className="ml-2 text-sm text-muted">{quote.name}</span>}
                </Link>
                <div className="flex items-start gap-3">
                  {quote && (
                    <div className="text-right tabular-nums">
                      <p className="font-semibold">{f.price(quote.lastPrice)}</p>
                      <p className={`text-sm ${changeColor(quote.changePercent)}`}>{f.percent(quote.changePercent)}</p>
                    </div>
                  )}
                  <form action={toggleWatch}>
                    <input type="hidden" name="ticker" value={ticker} />
                    <button type="submit" className="rounded-md px-2 py-1 text-xs text-faint hover:bg-chip hover:text-fg"
                      aria-label={`${t.watchlist.remove} ${ticker}`}>
                      ✕ {t.watchlist.remove}
                    </button>
                  </form>
                </div>
              </div>
              <div className="mt-2 text-sm">
                {quote && !quote.brokerTracked ? (
                  <p className="text-faint">{t.watchlist.notTracked}</p>
                ) : (
                  <Suspense fallback={<p className="text-faint">{t.watchlist.loadingBrokers}</p>}>
                    <BrokerLine ticker={ticker} radar={radar} locale={locale} t={t} f={f} />
                  </Suspense>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

async function BrokerLine({
  ticker,
  radar,
  locale,
  t,
  f,
}: {
  ticker: string;
  radar: Promise<Radar | null>;
  locale: Locale;
  t: Dictionary;
  f: Format;
}) {
  const data = await radar;
  if (!data) return <p className="text-faint">{t.watchlist.brokerUnavailable}</p>;
  const row = data.rows.find((r) => r.ticker === ticker);
  if (!row) return <p className="text-faint">{t.watchlist.noBrokerData}</p>;
  const status = STATUS[row.status];
  const streak =
    row.accumulationStreak > 0
      ? t.radar.streakUp(row.accumulationStreak)
      : row.accumulationStreak < 0
        ? t.radar.streakDown(-row.accumulationStreak)
        : null;
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
      <span className="text-xs text-faint">{t.watchlist.brokerFlow}</span>
      <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${status.className}`}>
        <span aria-hidden>{status.icon}</span>
        {t.broker.status[row.status]}
      </span>
      {streak && <span className={changeColor(row.accumulationStreak)}>{streak}</span>}
      {row.streakBroker && (
        <Link href={`/${locale}/brokers/${row.streakBroker.code}`} className="text-muted hover:text-accent hover:underline">
          {t.watchlist.leadBroker(row.streakBroker.code, row.streakBroker.days)}
        </Link>
      )}
      {row.exitGap != null && (
        <span className="text-muted">
          {t.radar.costGap} {f.signedFraction(row.exitGap)}
        </span>
      )}
    </div>
  );
}
