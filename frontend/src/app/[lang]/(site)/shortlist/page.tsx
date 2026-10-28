import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { HscBadge } from "@/components/hsc-badge";
import { getShortlist, type Shortlist, type ShortlistCandidate } from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { describe } from "@/lib/i18n/reasons";

/** Under this many replayed days the measured record is shown as "far too early". */
const MIN_SIGNAL_DAYS = 60;

export default async function ShortlistPage() {
  const { t } = await getI18n();
  const s = t.shortlist;
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold">{s.title}</h1>
          <p className="mt-2 text-sm text-muted">{s.intro}</p>
        </div>

        <aside className="rounded-xl border border-warn-line bg-warn-bg p-4 text-sm text-warn-fg">
          <p className="font-semibold">⚠ {s.disclaimerTitle}</p>
          <p className="mt-1 leading-relaxed">{s.disclaimer}</p>
        </aside>

        <details className="rounded-xl border border-line bg-card p-4 text-sm">
          <summary className="cursor-pointer font-semibold">{s.methodTitle}</summary>
          <dl className="mt-3 space-y-3">
            {s.groups.map((g) => (
              <div key={g.name}>
                <dt className="font-semibold">{g.name}</dt>
                <dd className="mt-0.5 leading-relaxed text-muted">{g.text}</dd>
              </div>
            ))}
          </dl>
        </details>

        <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>
          <ShortlistBody />
        </Suspense>
      </div>
    </main>
  );
}

async function ShortlistBody() {
  const { locale, t, f } = await getI18n();
  const s = t.shortlist;
  let list: Shortlist;
  try {
    list = await getShortlist();
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{s.unavailable}</div>;
  }
  if (list.candidates.length === 0) {
    return <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{s.empty}</p>;
  }
  const top = list.candidates.slice(0, list.size);
  const others = list.candidates.slice(list.size);
  const fiveDay = (group: string) =>
    list.record?.groups.find((g) => g.group === group)?.horizons.find((h) => h.days === 5)?.averageExcess ?? null;
  const topExcess = fiveDay("TOP_PICKS");
  const allExcess = fiveDay("ALL");

  return (
    <div className="space-y-6">
      <p className="text-xs text-faint">
        {list.priceDate && list.brokerDate && s.dataAsOf(f.tradingDate(list.priceDate), f.tradingDate(list.brokerDate))}
        {" · "}
        {s.scope(list.candidates.length)}
      </p>

      {list.record && topExcess != null && allExcess != null && (
        <section className="rounded-xl border border-line bg-card p-4 text-sm">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-muted">{s.measuredTitle}</h2>
          <p className="mt-1 leading-relaxed">
            {s.measured(f.signedFraction(topExcess), f.signedFraction(allExcess), list.record.signalDays)}
          </p>
          {list.record.signalDays < MIN_SIGNAL_DAYS && <p className="mt-1 text-warn-fg">{s.tooEarly}</p>}
          <dl className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
            {list.record.groups.map((g) => {
              const h = g.horizons.find((x) => x.days === 5);
              return (
                <div key={g.group} className="rounded-lg bg-chip px-2 py-1.5">
                  <dt className="text-faint">{s.groupsRecord[g.group] ?? g.group}</dt>
                  <dd className={`font-semibold ${changeColor(h?.averageExcess)}`}>
                    {f.signedFraction(h?.averageExcess)} <span className="font-normal text-faint">· {t.radar.record.trades(h?.trades ?? 0)}</span>
                  </dd>
                </div>
              );
            })}
          </dl>
        </section>
      )}

      <ol className="space-y-3">
        {top.map((c, i) => (
          <CandidateCard key={c.ticker} candidate={c} rank={i + 1} total={list.checks} locale={locale} t={t} f={f} />
        ))}
      </ol>

      {others.length > 0 && (
        <details className="rounded-xl border border-line bg-card p-4 text-sm">
          <summary className="cursor-pointer font-semibold">{s.othersTitle(others.length)}</summary>
          <ul className="mt-3 divide-y divide-line">
            {others.map((c) => (
              <li key={c.ticker} className="flex items-center justify-between gap-3 py-2">
                <Link href={`/${locale}/stocks/${c.ticker}`} className="min-w-0 flex-1 hover:text-accent">
                  <span className="font-mono font-semibold">{c.ticker}</span>
                  <span className="ml-2 truncate text-xs text-faint">{c.name}</span>
                </Link>
                <span className="shrink-0 text-xs text-muted">{s.passed(c.passed, list.checks)}</span>
              </li>
            ))}
          </ul>
        </details>
      )}

      <p className="text-xs text-faint">{s.disclaimer}</p>
    </div>
  );
}

function CandidateCard({
  candidate: c,
  rank,
  total,
  locale,
  t,
  f,
}: {
  candidate: ShortlistCandidate;
  rank: number;
  total: number;
  locale: Locale;
  t: Dictionary;
  f: Format;
}) {
  return (
    <li className="rounded-xl border border-line bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <Link href={`/${locale}/stocks/${c.ticker}`} className="group min-w-0 flex-1">
          <span className="text-xs text-faint">{t.shortlist.rank(rank)}</span>
          <span className="block">
            <span className="font-mono text-lg font-bold group-hover:text-accent group-hover:underline">{c.ticker}</span>
            {c.hsc && <HscBadge label={t.hsc.badge} title={t.hsc.title} />}
          </span>
          <span className="block truncate text-sm text-muted">{c.name}</span>
        </Link>
        <div className="shrink-0 text-right tabular-nums">
          <p className="font-semibold">{f.price(c.lastPrice)}</p>
          <p className={`text-sm ${changeColor(c.changePercent)}`}>{f.percent(c.changePercent)}</p>
        </div>
      </div>

      <div className="mt-2 flex items-center gap-2">
        <div className="flex gap-0.5" aria-hidden>
          {c.checks.map((check) => (
            <span key={check.code} className={`h-1.5 w-4 rounded-full ${check.points ? "bg-up" : "bg-chip"}`} />
          ))}
        </div>
        <span className="text-sm font-semibold">{t.shortlist.passed(c.passed, total)}</span>
      </div>

      <ul className="mt-3 space-y-1.5 text-sm leading-relaxed">
        {c.checks.map((check) => (
          <li key={check.code} className="flex gap-2">
            <span aria-hidden className={check.points ? "text-up" : "text-faint"}>
              {check.points ? "✓" : "✗"}
            </span>
            <span className={check.points ? "" : "text-muted"}>{describe(check, t, f)}</span>
          </li>
        ))}
      </ul>
    </li>
  );
}
