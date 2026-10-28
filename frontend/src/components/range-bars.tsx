"use client";

import { useMemo } from "react";
import { makeFormat } from "@/lib/format";
import type { Locale } from "@/lib/i18n/locales";
import { isLiveTrading, todaysQuote, useLiveQuote } from "@/lib/live-store";

type Row = { label: string; low: number | null; high: number | null };

/**
 * Where the price sits in its 1-month and 52-week range. The ranges come from end-of-day data; while
 * the market trades the dot follows the live price (same shared poller as the live box), and a live
 * price beyond the old range stretches it.
 */
export function RangeBars({
  ticker,
  locale,
  close,
  rows,
  title,
}: {
  ticker: string;
  locale: Locale;
  /** Last end-of-day close, used outside the session. */
  close: number | null;
  rows: Row[];
  title: string;
}) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const state = useLiveQuote(ticker);
  const live = isLiveTrading(state) ? state.result!.quote!.lastPrice : null;
  const price = todaysQuote(state)?.lastPrice ?? close;

  return (
    <div className="mt-4">
      <p className="text-xs text-faint">{title}</p>
      <dl className="mt-1 space-y-3 text-xs tabular-nums">
        {rows.map((row) => {
          const low = row.low != null && price != null ? Math.min(row.low, price) : row.low;
          const high = row.high != null && price != null ? Math.max(row.high, price) : row.high;
          const span = low != null && high != null && price != null && high > low ? high - low : null;
          const position = span == null ? null : (price! - low!) / span;
          return (
            <div key={row.label}>
              <dt className="text-muted">{row.label}</dt>
              <dd className="mt-0.5 flex items-center gap-2 pt-4">
                <span className="w-14 text-right text-muted">{f.price(low)}</span>
                <span className="relative h-1.5 flex-1 rounded-full bg-chip">
                  {position != null && (
                    <>
                      {/* The price sits above its dot: phones can't hover, so it is always shown. */}
                      <span
                        className={`absolute -top-5 -translate-x-1/2 whitespace-nowrap font-semibold ${live != null ? "text-up" : "text-fg"}`}
                        style={{ left: `${Math.min(92, Math.max(8, position * 100))}%` }}
                      >
                        {f.price(price)}
                      </span>
                      <span
                        aria-hidden
                        className="absolute top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-card bg-fg"
                        style={{ left: `${position * 100}%` }}
                      />
                    </>
                  )}
                </span>
                <span className="w-14 text-muted">{f.price(high)}</span>
              </dd>
            </div>
          );
        })}
      </dl>
    </div>
  );
}
