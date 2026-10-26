import type { BrokerPosition, BrokerSummary, FlowStatus } from "@/lib/api";
import { formatLots, formatPrice, formatSignedRupiah, formatTradingDate } from "@/lib/format";

// Status always shows an icon and a word, never colour alone.
const STATUS: Record<FlowStatus, { label: string; icon: string; className: string }> = {
  ACCUMULATION: { label: "Accumulation", icon: "▲", className: "border-emerald-500/50 bg-emerald-500/10 text-emerald-300" },
  NEUTRAL: { label: "Neutral", icon: "●", className: "border-slate-600 bg-slate-800/60 text-slate-200" },
  DISTRIBUTION: { label: "Distribution", icon: "▼", className: "border-red-500/50 bg-red-500/10 text-red-300" },
};

export function BrokerActivity({ summary }: { summary: BrokerSummary }) {
  const status = STATUS[summary.assessment.status];

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
          Broker activity · {summary.range}
          {summary.demo && (
            <span className="ml-2 rounded bg-amber-500/15 px-1.5 py-0.5 text-xs text-amber-300">
              DEMO DATA
            </span>
          )}
        </h2>
        <p className="text-xs text-slate-500">
          {formatTradingDate(summary.from)} – {formatTradingDate(summary.to)} · Source: {summary.dataSource}
        </p>
      </div>

      {!summary.demo && summary.brokerDays < summary.tradingDays && (
        <p className="rounded-lg border border-sky-500/40 bg-sky-500/10 px-4 py-2 text-sm text-sky-200">
          Broker data covers {summary.brokerDays} of {summary.tradingDays} trading days in this range.
          History is still being imported (newest days first) and fills in a little more each day.
        </p>
      )}

      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
        <span
          className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-semibold ${status.className}`}
        >
          <span aria-hidden>{status.icon}</span>
          {status.label}
        </span>
        <ul className="mt-4 list-disc space-y-1.5 pl-5 text-sm text-slate-300">
          {summary.assessment.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
        <p className="mt-4 text-xs text-slate-500">
          Rule-based description of past broker flow, not a forecast or a recommendation to buy
          or sell. The rules have not been backtested yet.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <BrokerTable title="Top net buyers" positions={summary.topBuyers} side="buy" />
        <BrokerTable title="Top net sellers" positions={summary.topSellers} side="sell" />
      </div>

      <details className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 text-sm text-slate-300">
        <summary className="cursor-pointer font-semibold">How to read this</summary>
        <dl className="mt-3 space-y-3">
          <Term name="Top-5 concentration">
            Each day: lots net bought by the 5 biggest net buyers, minus lots net sold by the 5
            biggest net sellers, as a share of that day&apos;s volume. Above zero means buying is
            concentrated in a few brokers while selling is spread across many — a common sign of
            accumulation. Below zero is the reverse.
          </Term>
          <Term name="Buyers / sellers">
            How many brokers ended the day as net buyers vs net sellers. Fewer buyers than sellers
            means the buying sits in fewer hands.
          </Term>
          <Term name="Foreign net">
            Net value bought by foreign investors, across all brokers they traded through.
          </Term>
          <Term name="Average price">
            Value divided by volume for that broker over the period: roughly where its position
            was built (buyers) or unwound (sellers).
          </Term>
        </dl>
      </details>
    </section>
  );
}

function BrokerTable({
  title,
  positions,
  side,
}: {
  title: string;
  positions: BrokerPosition[];
  side: "buy" | "sell";
}) {
  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h3>
      {positions.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">None in this period.</p>
      ) : (
        <table className="mt-3 w-full text-sm tabular-nums">
          <thead className="text-left text-xs text-slate-500">
            <tr>
              <th className="w-2/5 py-1 font-medium">Broker</th>
              <th className="py-1 text-right font-medium">Net lot</th>
              <th className="py-1 text-right font-medium">Net value</th>
              <th className="py-1 text-right font-medium">Avg {side === "buy" ? "buy" : "sell"}</th>
            </tr>
          </thead>
          <tbody>
            {positions.map((p) => (
              <tr key={p.brokerCode} className="border-t border-slate-800">
                <td className="max-w-0 py-2 pr-2">
                  <div className="font-mono font-semibold">{p.brokerCode}</div>
                  {p.brokerName && (
                    <div className="truncate text-xs text-slate-500" title={p.brokerName}>
                      {p.brokerName}
                    </div>
                  )}
                </td>
                <td className="py-2 text-right">{formatLots(Math.abs(p.netVolume))}</td>
                <td className="py-2 text-right">{formatSignedRupiah(p.netValue)}</td>
                <td className="py-2 text-right">
                  {formatPrice(side === "buy" ? p.buyAveragePrice : p.sellAveragePrice)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Term({ name, children }: { name: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="font-semibold text-slate-200">{name}</dt>
      <dd className="mt-0.5 text-slate-400">{children}</dd>
    </div>
  );
}
