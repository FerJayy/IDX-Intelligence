import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import {
  getRadar,
  RADAR_WINDOWS,
  type BrokerStockStreak,
  type FlowStatus,
  type Radar,
  type RadarRow,
  type RadarWindow,
} from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { describe } from "@/lib/i18n/reasons";

// Status always shows an icon and a word, never colour alone.
const VIEWS = ["cards", "streaks", "table"] as const;
type View = (typeof VIEWS)[number];

const STATUS: Record<FlowStatus, { icon: string; className: string }> = {
  ACCUMULATION: { icon: "▲", className: "border-up-line bg-up-bg text-up" },
  NEUTRAL: { icon: "●", className: "border-line bg-chip text-fg" },
  DISTRIBUTION: { icon: "▼", className: "border-down-line bg-down-bg text-down" },
};

export default async function RadarPage({ searchParams }: PageProps<"/[lang]/radar">) {
  const { t } = await getI18n();
  const content = searchParams.then((query) => {
    const requested = Number(query.window);
    const window = (RADAR_WINDOWS as readonly number[]).includes(requested) ? (requested as RadarWindow) : 10;
    const view = VIEWS.find((v) => v === query.view) ?? "cards";
    return <RadarView window={window} view={view} />;
  });
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <h1 className="text-2xl font-bold">{t.radar.title}</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">{t.radar.intro}</p>
        <div className="mt-6">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

async function RadarView({ window, view }: { window: RadarWindow; view: View }) {
  const { locale, t, f } = await getI18n();
  let radar: Radar;
  try {
    radar = await getRadar(window);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>;
  }
  const ranked = radar.rows.filter((r) => !r.excludedBecause);
  const filtered = radar.rows.filter((r) => r.excludedBecause);

  return (
    <div className="space-y-8">
      <details className="rounded-xl border border-line bg-card p-4 text-sm">
        <summary className="cursor-pointer font-semibold">{t.radar.help}</summary>
        <dl className="mt-3 grid gap-3 sm:grid-cols-2">
          {t.radar.helpItems.map((item) => (
            <div key={item.term}>
              <dt className="font-semibold">{item.term}</dt>
              <dd className="mt-0.5 text-muted">{item.text}</dd>
            </div>
          ))}
        </dl>
      </details>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex gap-1 border-b border-line" aria-label={t.radar.title}>
          {VIEWS.map((v) => (
            <Link
              key={v}
              href={`/${locale}/radar?window=${radar.windowDays}&view=${v}`}
              scroll={false}
              aria-current={v === view ? "page" : undefined}
              className={`-mb-px border-b-2 px-3 pb-2 text-sm ${
                v === view ? "border-accent font-semibold text-fg" : "border-transparent text-muted hover:text-fg"
              }`}
            >
              {t.radar.views[v]}
            </Link>
          ))}
        </nav>
        <nav className="flex gap-1" aria-label={t.radar.window}>
          {RADAR_WINDOWS.map((w) => (
            <Link
              key={w}
              href={`/${locale}/radar?window=${w}&view=${view}`}
              scroll={false}
              aria-current={w === radar.windowDays ? "page" : undefined}
              className={`rounded-md px-3 py-1 text-sm ${
                w === radar.windowDays ? "bg-chip font-semibold text-fg" : "text-muted hover:bg-chip hover:text-fg"
              }`}
            >
              {t.radar.windowLabel(w)}
            </Link>
          ))}
        </nav>
        <p className="text-xs text-faint">
          {radar.brokerDataDate && <>{t.radar.dataAsOf(f.tradingDate(radar.brokerDataDate))} · </>}
          {t.radar.tracked(radar.trackedStocks)} · {t.radar.addMore}
        </p>
      </div>

      {view === "streaks" ? (
        <StreakList streaks={radar.streaks} locale={locale} t={t} f={f} />
      ) : ranked.length === 0 ? (
        <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.radar.empty}</p>
      ) : view === "table" ? (
        <RadarTable rows={ranked} locale={locale} t={t} f={f} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {ranked.map((row, i) => (
            <RadarCard key={row.ticker} row={row} rank={i + 1} locale={locale} t={t} f={f} />
          ))}
        </div>
      )}

      {view !== "streaks" && filtered.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t.radar.filteredTitle}</h2>
          <p className="mt-1 text-xs text-faint">{t.radar.filteredHint}</p>
          <ul className="mt-3 space-y-1 text-sm">
            {filtered.map((r) => (
              <li key={r.ticker} className="flex flex-wrap gap-x-2">
                <Link href={`/${locale}/stocks/${r.ticker}`} className="font-mono font-semibold hover:text-accent hover:underline">
                  {r.ticker}
                </Link>
                <span className="text-faint">{t.radar.score} {r.score}</span>
                <span className="text-muted">— {r.excludedBecause && describe(r.excludedBecause, t, f)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <ScreenerSection radar={radar} locale={locale} t={t} f={f} />
    </div>
  );
}

/** Score as a bar: centred on zero, green to the right, red to the left. */
function ScoreBar({ score }: { score: number }) {
  const width = (Math.min(Math.abs(score), 80) / 80) * 50;
  return (
    <div className="relative h-2 w-28 rounded-full bg-chip" aria-hidden>
      <div className="absolute inset-y-0 left-1/2 w-px bg-line" />
      <div
        className={`absolute inset-y-0 rounded-full ${score >= 0 ? "left-1/2 bg-up" : "right-1/2 bg-down"}`}
        style={{ width: `${width}%` }}
      />
    </div>
  );
}

/** The card's plain-language summary, built from the numbers (not from the scoring rules). */
function summary(row: RadarRow, t: Dictionary, f: Format): string[] {
  const lines: string[] = [];
  if (row.accumulationStreak > 0) lines.push(t.radar.say.buying(row.accumulationStreak));
  else if (row.accumulationStreak < 0) lines.push(t.radar.say.selling(-row.accumulationStreak));
  else lines.push(t.radar.say.noStreak);
  if (row.streakBroker && row.streakBroker.days >= 2) {
    lines.push(
      t.radar.say.lead(row.streakBroker.code, row.streakBroker.name ?? "", row.streakBroker.days,
        f.price(row.streakBroker.averageBuyPrice)),
    );
  }
  if (row.foreignNetValue != null && row.foreignNetValue !== 0) {
    lines.push(
      row.foreignNetValue > 0
        ? t.radar.say.foreignBought(f.rupiah(row.foreignNetValue))
        : t.radar.say.foreignSold(f.rupiah(Math.abs(row.foreignNetValue))),
    );
  }
  if (row.exitGap != null) {
    lines.push(row.exitGap >= 0 ? t.radar.say.aboveCost(f.fraction(row.exitGap)) : t.radar.say.belowCost(f.fraction(-row.exitGap)));
  }
  return lines;
}

function RadarCard({ row, rank, locale, t, f }: { row: RadarRow; rank: number; locale: Locale; t: Dictionary; f: Format }) {
  const status = STATUS[row.status];
  const streak =
    row.accumulationStreak > 0
      ? t.radar.streakUp(row.accumulationStreak)
      : row.accumulationStreak < 0
        ? t.radar.streakDown(-row.accumulationStreak)
        : t.radar.streakNone;
  return (
    <article className="rounded-xl border border-line bg-card p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs text-faint">#{rank}</p>
          <Link href={`/${locale}/stocks/${row.ticker}`} className="group">
            <span className="font-mono text-xl font-bold group-hover:text-accent group-hover:underline">{row.ticker}</span>
            <span className="block text-sm text-muted sm:ml-2 sm:inline">{row.name}</span>
          </Link>
        </div>
        <div className="flex flex-col items-end gap-1">
          <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${status.className}`}>
            <span aria-hidden>{status.icon}</span>
            {t.broker.status[row.status]}
          </span>
          <div className="flex items-center gap-2 text-xs">
            <ScoreBar score={row.score} />
            <span className={`font-semibold tabular-nums ${changeColor(row.score)}`}>
              {t.radar.score} {row.score}
            </span>
          </div>
        </div>
      </header>

      <ul className="mt-3 space-y-1 text-sm leading-relaxed">
        {summary(row, t, f).map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>

      <dl className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
        <Stat label={t.radar.streak} help={t.radar.helpItems[0].text} value={streak} className={changeColor(row.accumulationStreak)} />
        <Stat
          label={t.radar.leadBroker}
          help={t.radar.helpItems[1].text}
          value={row.streakBroker ? t.radar.leadBrokerValue(row.streakBroker.code, row.streakBroker.days) : t.radar.streakNone}
        />
        <Stat label={t.radar.foreign} help={t.radar.helpItems[2].text} value={f.signedRupiah(row.foreignNetValue)}
          className={changeColor(row.foreignNetValue)} />
        <Stat label={t.radar.costGap} help={t.radar.helpItems[3].text} value={f.signedFraction(row.exitGap)} />
      </dl>

      <details className="mt-3">
        <summary className="cursor-pointer text-xs text-accent">{t.radar.whyScore}</summary>
        <ul className="mt-1 list-disc space-y-0.5 pl-4 text-xs text-muted">
          {row.reasons.map((reason) => (
            <li key={reason.code}>
              {describe(reason, t, f)}
              {reason.points != null && (
                <span className={`ml-1 ${changeColor(reason.points)}`}>
                  ({reason.points > 0 ? "+" : ""}
                  {reason.points})
                </span>
              )}
            </li>
          ))}
        </ul>
      </details>
    </article>
  );
}

function Stat({ label, help, value, className = "text-fg" }: { label: string; help: string; value: string; className?: string }) {
  return (
    <div className="rounded-lg bg-card-strong px-2 py-1.5">
      <dt className="text-faint" title={help}>
        {label} <span aria-hidden className="cursor-help">ⓘ</span>
      </dt>
      <dd className={`mt-0.5 font-semibold tabular-nums ${className}`}>{value}</dd>
    </div>
  );
}

function StreakList({ streaks, locale, t, f }: { streaks: BrokerStockStreak[]; locale: Locale; t: Dictionary; f: Format }) {
  if (streaks.length === 0) {
    return <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.radar.noStreaks}</p>;
  }
  return (
    <section className="space-y-3">
      <p className="text-sm text-muted">{t.radar.streaksIntro}</p>
      <ul className="divide-y divide-line rounded-xl border border-line bg-card">
        {streaks.map((s) => (
          <li key={`${s.brokerCode}-${s.ticker}`} className="flex flex-wrap items-baseline gap-x-4 gap-y-1 px-4 py-3 text-sm">
            <Link
              href={`/${locale}/brokers/${s.brokerCode}`}
              className="font-mono font-semibold hover:text-accent hover:underline"
              title={s.brokerName ?? undefined}
            >
              {s.days >= 5 ? "🔥 " : ""}
              {s.brokerCode}
            </Link>
            <span aria-hidden className="text-faint">→</span>
            <Link href={`/${locale}/stocks/${s.ticker}`} className="font-mono font-semibold hover:text-accent hover:underline">
              {s.ticker}
            </Link>
            <span className="font-semibold text-up">{t.radar.streakLine(s.days)}</span>
            <span className="text-muted">{t.radar.streakAvg(f.price(s.averageBuyPrice))}</span>
            <span className="text-muted">{t.radar.streakNet(f.signedRupiah(s.netValue))}</span>
            {s.gap != null && <span className="text-muted">{t.radar.streakGap(f.signedFraction(s.gap))}</span>}
            <span className="w-full truncate text-xs text-faint">
              {s.brokerName ?? s.brokerCode} · {s.stockName}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function RadarTable({ rows, locale, t, f }: { rows: RadarRow[]; locale: Locale; t: Dictionary; f: Format }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-card">
      <table className="w-full min-w-[60rem] text-sm tabular-nums">
        <thead className="bg-card-strong text-left text-xs text-faint">
          <tr>
            <th className="px-3 py-2 font-medium">{t.radar.rank}</th>
            <th className="px-3 py-2 font-medium">{t.radar.stock}</th>
            <th className="px-3 py-2 text-right font-medium">{t.radar.score}</th>
            <th className="px-3 py-2 font-medium">{t.radar.status}</th>
            <th className="px-3 py-2 font-medium">{t.radar.streak}</th>
            <th className="px-3 py-2 font-medium">{t.radar.leadBroker}</th>
            <th className="px-3 py-2 text-right font-medium">{t.radar.concentration}</th>
            <th className="px-3 py-2 text-right font-medium">{t.radar.foreign}</th>
            <th className="px-3 py-2 text-right font-medium">{t.radar.costGap}</th>
            <th className="px-3 py-2 text-right font-medium">{t.radar.change5d}</th>
            <th className="px-3 py-2 font-medium">{t.radar.why}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => {
            const status = STATUS[r.status];
            return (
              <tr key={r.ticker} className="border-t border-line align-top">
                <td className="px-3 py-2 text-faint">{i + 1}</td>
                <td className="max-w-44 px-3 py-2">
                  <Link href={`/${locale}/stocks/${r.ticker}`} className="group block">
                    <span className="font-mono font-semibold group-hover:text-accent group-hover:underline">{r.ticker}</span>
                    <span className="block truncate text-xs text-faint">{r.name}</span>
                  </Link>
                </td>
                <td className={`px-3 py-2 text-right font-semibold ${changeColor(r.score)}`}>{r.score}</td>
                <td className="px-3 py-2">
                  <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${status.className}`}>
                    <span aria-hidden>{status.icon}</span>
                    {t.broker.status[r.status]}
                  </span>
                </td>
                <td className={`whitespace-nowrap px-3 py-2 ${changeColor(r.accumulationStreak)}`}>
                  {r.accumulationStreak > 0
                    ? t.radar.streakUp(r.accumulationStreak)
                    : r.accumulationStreak < 0
                      ? t.radar.streakDown(-r.accumulationStreak)
                      : t.radar.streakNone}
                </td>
                <td className="whitespace-nowrap px-3 py-2" title={r.streakBroker?.name ?? undefined}>
                  {r.streakBroker ? (
                    <>
                      <span className="font-mono">{t.radar.leadBrokerValue(r.streakBroker.code, r.streakBroker.days)}</span>
                      <span className="block text-xs text-faint">@ {f.price(r.streakBroker.averageBuyPrice)}</span>
                    </>
                  ) : (
                    t.radar.streakNone
                  )}
                </td>
                <td className={`px-3 py-2 text-right ${changeColor(r.avgConcentration)}`}>{f.signedFraction(r.avgConcentration)}</td>
                <td className={`px-3 py-2 text-right ${changeColor(r.foreignNetValue)}`}>{f.signedRupiah(r.foreignNetValue)}</td>
                <td className="px-3 py-2 text-right">{f.signedFraction(r.exitGap)}</td>
                <td className={`px-3 py-2 text-right ${changeColor(r.return5d)}`}>{f.signedFraction(r.return5d)}</td>
                <td className="min-w-56 px-3 py-2">
                  <details>
                    <summary className="cursor-pointer text-xs text-accent">{t.radar.reasonsCount(r.reasons.length)}</summary>
                    <ul className="mt-1 list-disc space-y-0.5 pl-4 text-xs text-muted">
                      {r.reasons.map((reason) => (
                        <li key={reason.code}>
                          {describe(reason, t, f)}
                          {reason.points != null && (
                            <span className={`ml-1 ${changeColor(reason.points)}`}>
                              ({reason.points > 0 ? "+" : ""}
                              {reason.points})
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                  </details>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ScreenerSection({ radar, locale, t, f }: { radar: Radar; locale: Locale; t: Dictionary; f: Format }) {
  const { picks, summary, averageClaimedWinRate } = radar.screener;
  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">{t.radar.screenerTitle}</h2>
        <p className="mt-1 max-w-3xl text-sm text-muted">{t.radar.screenerIntro}</p>
      </div>

      {picks.length === 0 ? (
        <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.radar.noPicks}</p>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-4">
            <div className="rounded-xl border border-line bg-card p-4 text-sm">
              <p className="text-muted">
                {t.radar.claimedWinRate(averageClaimedWinRate == null ? "—" : f.fraction(averageClaimedWinRate / 100))}
              </p>
            </div>
            {summary.map((s) => (
              <div key={s.days} className="rounded-xl border border-line bg-card p-4 text-sm">
                <p className="font-semibold">{t.radar.afterDays(s.days)}</p>
                {s.picks === 0 ? (
                  <p className="mt-1 text-faint">{t.radar.notYet}</p>
                ) : (
                  <dl className="mt-1 space-y-0.5 tabular-nums">
                    <div className="flex justify-between"><dt className="text-faint">{t.radar.picksMeasured(s.picks)}</dt></div>
                    <div className="flex justify-between"><dt className="text-faint">{t.radar.hitRate}</dt><dd>{f.fraction(s.hitRate)}</dd></div>
                    <div className="flex justify-between"><dt className="text-faint">{t.radar.avgReturn}</dt><dd className={changeColor(s.averageReturn)}>{f.signedFraction(s.averageReturn)}</dd></div>
                    <div className="flex justify-between"><dt className="text-faint">{t.radar.vsIhsg}</dt><dd className={changeColor(s.averageExcess)}>{f.signedFraction(s.averageExcess)}</dd></div>
                  </dl>
                )}
              </div>
            ))}
          </div>

          <div className="overflow-x-auto rounded-xl border border-line bg-card">
            <table className="w-full min-w-[40rem] text-sm tabular-nums">
              <thead className="bg-card-strong text-left text-xs text-faint">
                <tr>
                  <th className="px-3 py-2 font-medium">{t.radar.pickDate}</th>
                  <th className="px-3 py-2 font-medium">{t.radar.stock}</th>
                  <th className="px-3 py-2 font-medium">{t.radar.bucket}</th>
                  <th className="px-3 py-2 text-right font-medium">{t.radar.claimed}</th>
                  <th className="px-3 py-2 text-right font-medium">{t.radar.entry}</th>
                  {summary.map((s) => (
                    <th key={s.days} className="px-3 py-2 text-right font-medium">+{s.days}d</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {picks.map((p) => (
                  <tr key={`${p.pickDate}-${p.ticker}`} className="border-t border-line">
                    <td className="px-3 py-2">{f.tradingDate(p.pickDate)}</td>
                    <td className="px-3 py-2">
                      <Link href={`/${locale}/stocks/${p.ticker}`} className="font-mono font-semibold hover:text-accent hover:underline">
                        {p.ticker}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-xs text-muted">{p.bucket}</td>
                    <td className="px-3 py-2 text-right">{p.claimedWinRate == null ? "—" : f.fraction(p.claimedWinRate / 100)}</td>
                    <td className="px-3 py-2 text-right">{f.price(p.entryClose)}</td>
                    {p.returns.map((r, i) => (
                      <td key={i} className={`px-3 py-2 text-right ${changeColor(r)}`}>{f.signedFraction(r)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
