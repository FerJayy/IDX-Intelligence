"use client";

import { useMemo } from "react";
import { makeFormat } from "@/lib/format";
import type { Locale } from "@/lib/i18n/locales";
import { todaysQuote, useLiveQuote } from "@/lib/live-store";

/**
 * A price-dependent line, recomputed from the live price with the backend's thresholds
 * (StockSummaryService): trend vs the 20-day average, gap to the main buyers' cost, near a 52-week extreme.
 */
export type LiveRule =
  | { kind: "trend"; ma20: number; rising: boolean }
  | { kind: "cost"; averagePrice: number }
  | { kind: "range"; high: number; low: number };

/** text = the server's end-of-day wording (null when the line doesn't apply at the last close). */
export type SummaryLine = { key: string; text: string | null; points: number | null; live?: LiveRule };

/** Plain-string templates (Client Components get no functions): {ma20}, {gap}, {price}. */
export type LiveLabels = {
  trendUp: string;
  trendDown: string;
  trendSideways: string;
  costFar: string;
  costNear: string;
  costBelow: string;
  nearHigh: string;
  nearLow: string;
  liveNote: string;
};

export function SummaryLines({
  ticker,
  locale,
  lines,
  labels,
}: {
  ticker: string;
  locale: Locale;
  lines: SummaryLine[];
  labels: LiveLabels;
}) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const state = useLiveQuote(ticker);
  const price = todaysQuote(state)?.lastPrice ?? null;
  const fill = (template: string, values: Record<string, string>) =>
    template.replace(/\{(\w+)\}/g, (_, key: string) => values[key] ?? "");

  /** The line for the live price, or the server's line when there is none (or no rule). */
  const resolve = (line: SummaryLine): { text: string | null; points: number | null } => {
    const rule = line.live;
    if (price == null || !rule) return line;
    switch (rule.kind) {
      case "trend": {
        const ma20 = f.price(rule.ma20);
        if (price > rule.ma20 && rule.rising) return { text: fill(labels.trendUp, { ma20 }), points: 1 };
        if (price < rule.ma20 && !rule.rising) return { text: fill(labels.trendDown, { ma20 }), points: -1 };
        return { text: fill(labels.trendSideways, { ma20 }), points: 0 };
      }
      case "cost": {
        const gap = price / rule.averagePrice - 1;
        const values = { gap: f.fraction(Math.abs(gap)), price: f.price(rule.averagePrice) };
        if (gap > 0.1) return { text: fill(labels.costFar, values), points: -1 };
        if (gap < -0.05) return { text: fill(labels.costBelow, values), points: 0 };
        return { text: fill(labels.costNear, values), points: 0 };
      }
      case "range": {
        if (price >= rule.high * 0.95) return { text: fill(labels.nearHigh, { price: f.price(rule.high) }), points: 0 };
        if (price <= rule.low * 1.1) return { text: fill(labels.nearLow, { price: f.price(rule.low) }), points: 0 };
        return { text: null, points: null };
      }
    }
  };

  const shown = lines.map((line) => ({ key: line.key, live: price != null && line.live != null, ...resolve(line) }));
  return (
    <>
      <ul className="mt-3 space-y-1.5 text-sm leading-relaxed">
        {shown
          .filter((line) => line.text)
          .map((line) => (
            <li key={line.key} className="flex gap-2">
              <span
                aria-hidden
                className={line.points == null || line.points === 0 ? "text-faint" : line.points > 0 ? "text-up" : "text-warn-fg"}
              >
                {line.points != null && line.points < 0 ? "⚠" : "•"}
              </span>
              <span>
                {line.text}
                {line.live && <span className="ml-1 text-xs text-up">●</span>}
              </span>
            </li>
          ))}
      </ul>
      {price != null && <p className="mt-1 text-xs text-faint"><span className="text-up">●</span> {labels.liveNote}</p>}
    </>
  );
}
