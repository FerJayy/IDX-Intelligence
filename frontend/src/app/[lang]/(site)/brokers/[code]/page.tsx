import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { HscBadge } from "@/components/hsc-badge";
import {
  BROKER_WINDOWS,
  getBrokerProfile,
  type BrokerProfile,
  type BrokerStockPosition,
  type BrokerWindow,
} from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";

export default async function BrokerPage({ params, searchParams }: PageProps<"/[lang]/brokers/[code]">) {
  const { locale, t } = await getI18n();
  // code and searchParams are request-time data, read inside <Suspense> (cacheComponents).
  const content = Promise.all([params, searchParams]).then(([{ code }, query]) => {
    const requested = Number(query.window);
    const window = (BROKER_WINDOWS as readonly number[]).includes(requested) ? (requested as BrokerWindow) : 20;
    return <Profile code={code.toUpperCase()} window={window} />;
  });
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-5xl">
        <Link href={`/${locale}/brokers`} className="text-sm text-muted hover:text-fg">
          {t.brokers.back}
        </Link>
        <div className="mt-4">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

async function Profile({ code, window }: { code: string; window: BrokerWindow }) {
  const { locale, t, f } = await getI18n();
  if (!/^[A-Z0-9]{2,4}$/.test(code)) notFound();
  let profile: BrokerProfile | null;
  try {
    profile = await getBrokerProfile(code, window);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>;
  }
  if (!profile) notFound();

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-mono text-3xl font-bold">{profile.code}</h1>
          <p className="text-fg">{profile.name}</p>
          {profile.from && profile.to && (
            <p className="text-xs text-faint">{t.brokers.period(f.tradingDate(profile.from), f.tradingDate(profile.to))}</p>
          )}
        </div>
        <dl className="flex gap-6 text-right tabular-nums">
          <div>
            <dt className="text-xs text-faint">{t.brokers.totalGross}</dt>
            <dd className="text-lg font-semibold">{f.rupiah(profile.grossValue)}</dd>
          </div>
          <div>
            <dt className="text-xs text-faint">{t.brokers.totalNet}</dt>
            <dd className={`text-lg font-semibold ${changeColor(profile.netValue)}`}>{f.signedRupiah(profile.netValue)}</dd>
          </div>
        </dl>
      </header>

      <nav className="flex gap-1" aria-label={t.brokers.window}>
        {BROKER_WINDOWS.map((w) => (
          <Link
            key={w}
            href={`/${locale}/brokers/${profile.code}?window=${w}`}
            scroll={false}
            aria-current={w === profile.windowDays ? "page" : undefined}
            className={`rounded-md px-3 py-1 text-sm ${
              w === profile.windowDays ? "bg-chip font-semibold text-fg" : "text-muted hover:bg-chip hover:text-fg"
            }`}
          >
            {t.brokers.windowLabel(w)}
          </Link>
        ))}
      </nav>

      <div className="grid gap-6 lg:grid-cols-2">
        <PositionList title={t.brokers.accumulating} hint={t.brokers.accumulatingHint} positions={profile.accumulating}
          side="buy" locale={locale} t={t} f={f} />
        <PositionList title={t.brokers.distributing} hint={t.brokers.distributingHint} positions={profile.distributing}
          side="sell" locale={locale} t={t} f={f} />
      </div>

      <p className="text-xs text-faint">{t.brokers.note}</p>
    </div>
  );
}

function PositionList({
  title,
  hint,
  positions,
  side,
  locale,
  t,
  f,
}: {
  title: string;
  hint: string;
  positions: BrokerStockPosition[];
  side: "buy" | "sell";
  locale: Locale;
  t: Dictionary;
  f: Format;
}) {
  return (
    <section>
      <h2 className={`text-lg font-semibold ${side === "buy" ? "text-up" : "text-down"}`}>
        {side === "buy" ? "▲" : "▼"} {title}
      </h2>
      <p className="mt-1 text-xs text-faint">{hint}</p>
      {positions.length === 0 ? (
        <p className="mt-3 rounded-xl border border-line bg-card p-4 text-sm text-faint">{t.brokers.none}</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {positions.map((p) => {
            const price = side === "buy" ? p.averageBuyPrice : p.averageSellPrice;
            const lots = f.lots(Math.abs(p.netVolume));
            const value = f.rupiah(Math.abs(p.netValue));
            return (
              <li key={p.ticker} className="rounded-xl border border-line bg-card p-3 text-sm">
                <div className="flex items-baseline justify-between gap-2">
                  <Link href={`/${locale}/stocks/${p.ticker}`} className="group min-w-0">
                    <span className="font-mono font-semibold group-hover:text-accent group-hover:underline">{p.ticker}</span>
                    {p.hsc && <HscBadge label={t.hsc.badge} title={t.hsc.title} />}
                    <span className="ml-2 truncate text-xs text-faint">{p.stockName}</span>
                  </Link>
                  <span className={`font-semibold tabular-nums ${changeColor(p.netValue)}`}>{f.signedRupiah(p.netValue)}</span>
                </div>
                <p className="mt-1 leading-relaxed text-muted">
                  {side === "buy" ? t.brokers.boughtLine(value, lots, f.price(price)) : t.brokers.soldLine(value, lots, f.price(price))}{" "}
                  {p.gap != null && (p.gap >= 0 ? t.brokers.gapAbove(f.fraction(p.gap)) : t.brokers.gapBelow(f.fraction(-p.gap)))}{" "}
                  {p.buyStreak >= 2 ? <span className="text-up">{t.brokers.streak(p.buyStreak)}</span> : t.brokers.activeDays(p.days)}
                  {p.foreignNetValue != null && p.foreignNetValue !== 0 && <> {t.brokers.foreign(f.signedRupiah(p.foreignNetValue))}</>}
                </p>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
