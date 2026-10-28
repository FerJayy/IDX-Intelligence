import Link from "next/link";
import type { BrokerPosition, BrokerSummary, FlowStatus } from "@/lib/api";
import type { Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { describe } from "@/lib/i18n/reasons";

// Status always shows an icon and a word, never colour alone.
const STATUS: Record<FlowStatus, { icon: string; className: string }> = {
  ACCUMULATION: { icon: "▲", className: "border-up-line bg-up-bg text-up" },
  NEUTRAL: { icon: "●", className: "border-line bg-chip text-fg" },
  DISTRIBUTION: { icon: "▼", className: "border-down-line bg-down-bg text-down" },
};

export type PeriodLink = { code: string; label: string; href: string; active: boolean };

export async function BrokerActivity({ summary, periods }: { summary: BrokerSummary; periods: PeriodLink[] }) {
  const { locale, t, f } = await getI18n();
  const status = STATUS[summary.assessment.status];
  const active = periods.find((p) => p.active);
  // Same minimum as the backend's assessment (BrokerAnalysisService.MIN_DAYS_FOR_ASSESSMENT).
  const shortPeriod = summary.brokerDays < 5;

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">
          {t.broker.title(active?.label ?? summary.range)}
          {summary.demo && (
            <span className="ml-2 rounded bg-warn-bg px-1.5 py-0.5 text-xs text-warn-fg">{t.broker.demoTag}</span>
          )}
        </h2>
        <p className="text-xs text-faint">
          {summary.brokerDays === 0
            ? t.broker.noDays
            : summary.from === summary.to
              ? t.broker.singleDay(f.tradingDate(summary.to))
              : t.broker.totalOver(f.tradingDate(summary.from), f.tradingDate(summary.to), summary.brokerDays)}{" "}
          · {t.common.source}: {summary.dataSource}
        </p>
      </div>

      <nav className="flex flex-wrap gap-1" aria-label={t.broker.periodLabel}>
        {periods.map((p) => (
          <Link
            key={p.code}
            href={p.href}
            scroll={false}
            aria-current={p.active ? "page" : undefined}
            className={`rounded-md px-3 py-1 text-sm ${p.active ? "bg-chip font-semibold text-fg" : "text-muted hover:bg-chip hover:text-fg"}`}
          >
            {p.label}
          </Link>
        ))}
      </nav>

      {!summary.demo && summary.brokerDays < summary.tradingDays && (
        <p className="rounded-lg border border-info-line bg-info-bg px-4 py-2 text-sm text-info-fg">
          {t.broker.coverage(summary.brokerDays, summary.tradingDays)}
        </p>
      )}

      <div className="rounded-xl border border-line bg-card p-5">
        {/* A day or two is too short for a reading: no badge, only the reason that says so. */}
        {!shortPeriod && (
          <span
            className={`mb-4 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-semibold ${status.className}`}
          >
            <span aria-hidden>{status.icon}</span>
            {t.broker.status[summary.assessment.status]}
          </span>
        )}
        <ul className="list-disc space-y-1.5 pl-5 text-sm text-fg">
          {summary.assessment.reasons.map((reason) => (
            <li key={reason.code}>{describe(reason, t, f)}</li>
          ))}
        </ul>
        <p className="mt-4 text-xs text-faint">{t.broker.disclaimer}</p>
      </div>

      {/* Short periods are mostly about who bought and sold: open the tables right away. */}
      <details className="rounded-xl border border-line bg-card p-4" open={shortPeriod || active?.code === "1D" || active?.code === "1W"}>
        <summary className="cursor-pointer text-sm font-semibold">{t.broker.showTables}</summary>
        <div className="mt-3 grid gap-4 lg:grid-cols-2">
          <BrokerTable title={t.broker.topBuyers} positions={summary.topBuyers} side="buy" locale={locale} t={t} f={f} />
          <BrokerTable title={t.broker.topSellers} positions={summary.topSellers} side="sell" locale={locale} t={t} f={f} />
        </div>
      </details>

      <details className="rounded-xl border border-line bg-card p-4 text-sm">
        <summary className="cursor-pointer font-semibold">{t.broker.howToRead}</summary>
        <dl className="mt-3 space-y-3">
          {t.broker.terms.map((term) => (
            <div key={term.name}>
              <dt className="font-semibold">{term.name}</dt>
              <dd className="mt-0.5 text-muted">{term.text}</dd>
            </div>
          ))}
        </dl>
      </details>
    </section>
  );
}

function BrokerTable({
  title,
  positions,
  side,
  locale,
  t,
  f,
}: {
  title: string;
  positions: BrokerPosition[];
  side: "buy" | "sell";
  locale: Locale;
  t: Dictionary;
  f: Format;
}) {
  return (
    <section className="rounded-xl border border-line bg-card p-4">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-muted">{title}</h3>
      {positions.length === 0 ? (
        <p className="mt-4 text-sm text-faint">{t.broker.none}</p>
      ) : (
        <table className="mt-3 w-full text-xs tabular-nums sm:text-sm">
          <thead className="text-left text-xs text-faint">
            <tr>
              <th className="w-2/5 py-1 font-medium">{t.broker.broker}</th>
              <th className="py-1 text-right font-medium">{t.broker.netLot}</th>
              <th className="py-1 text-right font-medium">{t.broker.netValue}</th>
              <th className="py-1 text-right font-medium">{side === "buy" ? t.broker.avgBuy : t.broker.avgSell}</th>
            </tr>
          </thead>
          <tbody>
            {positions.map((p) => (
              <tr key={p.brokerCode} className="border-t border-line">
                <td className="max-w-0 py-2 pr-2">
                  <Link href={`/${locale}/brokers/${p.brokerCode}`} className="font-mono font-semibold hover:text-accent hover:underline">
                    {p.brokerCode}
                  </Link>
                  {p.brokerName && (
                    <div className="truncate text-xs text-faint" title={p.brokerName}>
                      {p.brokerName}
                    </div>
                  )}
                </td>
                <td className="whitespace-nowrap py-2 pl-2 text-right">{f.lots(Math.abs(p.netVolume))}</td>
                <td className="whitespace-nowrap py-2 pl-2 text-right">{f.signedRupiah(p.netValue)}</td>
                <td className="whitespace-nowrap py-2 pl-2 text-right">{f.price(side === "buy" ? p.buyAveragePrice : p.sellAveragePrice)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
