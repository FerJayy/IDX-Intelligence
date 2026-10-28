"use client";

import { useMemo } from "react";
import { changeColor, makeFormat } from "@/lib/format";
import type { ClientLabels, Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { useLiveQuote } from "@/lib/live-store";

const LOT_SIZE = 100;

/**
 * Real-time price box. The shared poller (lib/live-store) asks once on open, then every minute while
 * the tab is visible, for at most 15 minutes (then "Resume"). Shows nothing outside market hours.
 */
export function LivePrice({ ticker, locale, labels }: { ticker: string; locale: Locale; labels: ClientLabels<Dictionary["live"]> }) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const { result, paused, resume } = useLiveQuote(ticker);
  const status = result?.status;
  const halted = paused || status !== "LIVE";

  if (!result || status === "MARKET_CLOSED") return null;

  if (status !== "LIVE" || !result.quote) {
    return (
      <p className="rounded-lg border border-line bg-card px-4 py-2 text-sm text-muted">
        {status === "BUDGET_USED" ? labels.budgetUsed : labels.unavailable}
      </p>
    );
  }

  const q = result.quote;
  return (
    <section className={`rounded-xl border border-up-line bg-card p-4 ${halted ? "" : "live-breathe"}`} aria-live="polite">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <span className="inline-flex items-center gap-1.5 rounded bg-up-bg px-2 py-0.5 font-semibold text-up">
          <span className={`h-1.5 w-1.5 rounded-full bg-current ${halted ? "" : "animate-pulse"}`} aria-hidden />
          {labels.badge}
        </span>
        {q.dataTime && <span className="text-muted">{labels.updated.replace("{time}", q.dataTime)}</span>}
        {q.marketLabel && <span className="text-faint">{q.marketLabel}</span>}
      </div>
      <div className="mt-2 flex flex-wrap items-baseline gap-x-4 gap-y-1 tabular-nums">
        <span className="text-2xl font-bold">{f.price(q.lastPrice)}</span>
        <span className={changeColor(q.change)}>
          {f.change(q.change)} ({f.percent(q.changePercent)})
        </span>
        {q.noTradeToday ? (
          <span className="text-sm text-faint">{labels.noTradeToday}</span>
        ) : (
          <span className="text-sm text-muted">
            {labels.lots} {f.lots(q.lot == null ? null : q.lot * LOT_SIZE)} · {labels.value} {f.rupiah(q.value)} ·{" "}
            {labels.vwap} {f.price(q.vwap)}
          </span>
        )}
      </div>
      {paused ? (
        <p className="mt-2 text-xs text-faint">
          {labels.paused}{" "}
          <button type="button" onClick={resume} className="font-semibold text-accent hover:underline">
            {labels.resume}
          </button>
        </p>
      ) : (
        <p className="mt-2 text-xs text-faint">{labels.source}</p>
      )}
    </section>
  );
}
