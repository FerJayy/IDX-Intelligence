"use client";

import { useEffect, useMemo, useState } from "react";
import type { LiveResult } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";

const REFRESH_MS = 60_000;
/** Stop polling after this long so a forgotten tab can't use up the free daily limit. */
const STOP_AFTER_MS = 15 * 60_000;
const LOT_SIZE = 100;

const UNAVAILABLE: LiveResult = { status: "UNAVAILABLE", quote: null, fetchedAt: null, budgetLeft: 0 };

function fetchLive(ticker: string): Promise<LiveResult> {
  return fetch(`/api/live/${ticker}`, { cache: "no-store" })
    .then((response) => response.json() as Promise<LiveResult>)
    .catch(() => UNAVAILABLE);
}

/**
 * Real-time price box. Asks once on open, then every minute while the tab is visible, for at most
 * 15 minutes (then "Resume"). Shows nothing outside market hours: the page's end-of-day data applies.
 */
export function LivePrice({ ticker, locale, labels }: { ticker: string; locale: Locale; labels: Dictionary["live"] }) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const [result, setResult] = useState<LiveResult | null>(null);
  const [paused, setPaused] = useState(false);

  // Nothing more to ask once the market is closed or the budget is spent.
  const status = result?.status;
  const stopped = status === "MARKET_CLOSED" || status === "BUDGET_USED" || status === "UNAVAILABLE";
  const halted = paused || stopped;

  useEffect(() => {
    if (halted) return;
    let cancelled = false;
    const startedAt = Date.now();
    const tick = () =>
      fetchLive(ticker).then((next) => {
        if (!cancelled) setResult(next);
      });
    tick();
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > STOP_AFTER_MS) {
        setPaused(true);
        return;
      }
      // Background tabs don't spend requests; the next visible tick catches up.
      if (document.visibilityState === "visible") tick();
    }, REFRESH_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [ticker, halted]);

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
    <section className="rounded-xl border border-up-line bg-card p-4" aria-live="polite">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <span className="inline-flex items-center gap-1.5 rounded bg-up-bg px-2 py-0.5 font-semibold text-up">
          <span className={`h-1.5 w-1.5 rounded-full bg-current ${halted ? "" : "animate-pulse"}`} aria-hidden />
          {labels.badge}
        </span>
        {q.dataTime && <span className="text-muted">{labels.updated(q.dataTime)}</span>}
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
          <button type="button" onClick={() => setPaused(false)} className="font-semibold text-accent hover:underline">
            {labels.resume}
          </button>
        </p>
      ) : (
        <p className="mt-2 text-xs text-faint">{labels.source}</p>
      )}
    </section>
  );
}
