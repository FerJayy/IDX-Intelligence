import { unstable_rethrow } from "next/navigation";
import { getStockSummary, type FlowStatus, type StockSummary } from "@/lib/api";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import { describe } from "@/lib/i18n/reasons";
import { RangeBars } from "./range-bars";
import { SummaryLines, type LiveRule, type SummaryLine } from "./summary-lines";

// Status always shows an icon and a word, never colour alone.
const STATUS: Record<FlowStatus, { icon: string; className: string }> = {
  ACCUMULATION: { icon: "▲", className: "border-up-line bg-up-bg text-up" },
  NEUTRAL: { icon: "●", className: "border-line bg-chip text-fg" },
  DISTRIBUTION: { icon: "▼", className: "border-down-line bg-down-bg text-down" },
};
/** Enough lines to tell the story; the sections below hold the detail. */
const MAX_LINES = 6;

/** The short overview at the top of a stock page: broker flow per period and a few plain facts. */
export async function StockSummaryCard({ ticker }: { ticker: string }) {
  const { locale, t, f } = await getI18n();
  let summary: StockSummary;
  try {
    summary = await getStockSummary(ticker);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <p className="rounded-xl border border-line p-4 text-sm text-faint">{t.summary.unavailable}</p>;
  }
  const s = t.summary;
  const trend = summary.facts.find((r) => r.code.startsWith("SUM_TREND_"));
  const range = summary.facts.find((r) => r.code.startsWith("SUM_NEAR_52W_"));
  const others = summary.facts.filter((r) => r !== trend && r !== range);
  const brokers = brokersLine(summary, t);
  const n = (v: unknown) => Number(v);
  // Price-dependent lines carry a rule, so the browser can redo them with the live price.
  const liveRule = (code: string, v: Record<string, unknown>): LiveRule | undefined =>
    code.startsWith("SUM_TREND_")
      ? { kind: "trend", ma20: n(v.ma20), rising: v.rising === true }
      : code.endsWith("_BUYERS_COST")
        ? { kind: "cost", averagePrice: n(v.averagePrice) }
        : undefined;
  const levels = summary.levels;
  const lines: SummaryLine[] = [
    ...(trend ? [{ key: "trend", text: describe(trend, t, f), points: trend.points, live: liveRule(trend.code, trend.values) }] : []),
    ...(brokers ? [{ key: "brokers", text: brokers, points: null }] : []),
    ...others.map((r) => ({ key: r.code, text: describe(r, t, f), points: r.points, live: liveRule(r.code, r.values) })),
    // Shown only near a 52-week extreme, at the last close or at the live price.
    ...(levels?.fullYear && levels.yearHigh != null && levels.yearLow != null
      ? [{ key: "range", text: range ? describe(range, t, f) : null, points: range?.points ?? null,
          live: { kind: "range" as const, high: levels.yearHigh, low: levels.yearLow } }]
      : []),
  ].slice(0, MAX_LINES);

  return (
    <section className="rounded-xl border border-line bg-card p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{s.title}</h2>

      {summary.flows.length > 0 ? (
        <div className="mt-3">
          <p className="text-xs text-faint">{s.glance}</p>
          <div className="mt-1 grid grid-cols-3 gap-2">
            {summary.flows.map((flow) => {
              const status = STATUS[flow.status];
              return (
                <div key={flow.period} className={`rounded-lg border px-2 py-1.5 text-center ${status.className}`}>
                  <p className="text-xs opacity-80">{s.periods[flow.period]}</p>
                  <p className="text-sm font-semibold">
                    <span aria-hidden>{status.icon}</span> {t.broker.status[flow.status]}
                  </p>
                  {flow.brokerDays < 5 && <p className="text-[10px] opacity-80">{s.fewDays(flow.brokerDays)}</p>}
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        !summary.brokerTracked && <p className="mt-2 text-xs text-faint">{s.notTracked}</p>
      )}

      <SummaryLines ticker={summary.ticker} locale={locale} lines={lines} labels={s.live} />

      {summary.levels && (
        <RangeBars
          ticker={summary.ticker}
          locale={locale}
          close={summary.levels.last}
          title={s.levels}
          rows={[
            { label: s.monthRange, low: summary.levels.monthLow, high: summary.levels.monthHigh },
            { label: s.yearRange, low: summary.levels.yearLow, high: summary.levels.yearHigh },
          ]}
        />
      )}
      <p className="mt-3 text-xs text-faint">{s.note}</p>
    </section>
  );
}

/** "Big brokers: collecting over the last 10 days and 1 month; balanced over 3 months." */
function brokersLine(summary: StockSummary, t: Dictionary): string | null {
  if (summary.flows.length === 0) return null;
  const groups = new Map<FlowStatus, string[]>();
  for (const flow of summary.flows) {
    if (flow.brokerDays < 5) continue;
    groups.set(flow.status, [...(groups.get(flow.status) ?? []), t.summary.periodsInline[flow.period]]);
  }
  if (groups.size === 0) return null;
  const parts = [...groups.entries()].map(([status, periods]) =>
    t.summary.over(t.summary.flowWords[status], periods.join(t.summary.and)),
  );
  return t.summary.brokersLine(parts.join("; "));
}
