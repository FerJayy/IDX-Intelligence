import { unstable_rethrow } from "next/navigation";
import { getStockSummary, type FlowStatus, type StockSummary } from "@/lib/api";
import type { Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";
import { describe } from "@/lib/i18n/reasons";

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
  const { t, f } = await getI18n();
  let summary: StockSummary;
  try {
    summary = await getStockSummary(ticker);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <p className="rounded-xl border border-line p-4 text-sm text-faint">{t.summary.unavailable}</p>;
  }
  const s = t.summary;
  const [trend, ...others] = summary.facts.filter((r) => r.code.startsWith("SUM_TREND_")).concat(
    summary.facts.filter((r) => !r.code.startsWith("SUM_TREND_")),
  );
  const brokers = brokersLine(summary, t);
  const lines: { text: string; points: number | null }[] = [
    ...(trend ? [{ text: describe(trend, t, f), points: trend.points }] : []),
    ...(brokers ? [{ text: brokers, points: null }] : []),
    ...others.map((r) => ({ text: describe(r, t, f), points: r.points })),
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

      <ul className="mt-3 space-y-1.5 text-sm leading-relaxed">
        {lines.map((line) => (
          <li key={line.text} className="flex gap-2">
            <span aria-hidden className={line.points == null || line.points === 0 ? "text-faint" : line.points > 0 ? "text-up" : "text-warn-fg"}>
              {line.points != null && line.points < 0 ? "⚠" : "•"}
            </span>
            <span>{line.text}</span>
          </li>
        ))}
      </ul>

      {summary.levels && <Levels levels={summary.levels} t={t} f={f} />}
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

function Levels({ levels, t, f }: { levels: NonNullable<StockSummary["levels"]>; t: Dictionary; f: Format }) {
  const rows = [
    { label: t.summary.monthRange, low: levels.monthLow, high: levels.monthHigh },
    { label: t.summary.yearRange, low: levels.yearLow, high: levels.yearHigh },
  ];
  return (
    <div className="mt-4">
      <p className="text-xs text-faint">{t.summary.levels}</p>
      <dl className="mt-1 space-y-2 text-xs tabular-nums">
        {rows.map((row) => {
          const span = row.low != null && row.high != null && levels.last != null && row.high > row.low ? row.high - row.low : null;
          const position = span == null ? null : Math.min(1, Math.max(0, (levels.last! - row.low!) / span));
          return (
            <div key={row.label}>
              <dt className="text-muted">{row.label}</dt>
              <dd className="mt-0.5 flex items-center gap-2">
                <span className="w-14 text-right">{f.price(row.low)}</span>
                <span className="relative h-1.5 flex-1 rounded-full bg-chip" aria-hidden>
                  {position != null && (
                    <span className="absolute top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-card bg-fg"
                      style={{ left: `${position * 100}%` }} />
                  )}
                </span>
                <span className="w-14">{f.price(row.high)}</span>
              </dd>
            </div>
          );
        })}
      </dl>
    </div>
  );
}
