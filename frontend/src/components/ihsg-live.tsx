"use client";

import { useEffect, useMemo, useState } from "react";
import type { CandleSeries } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import type { Locale } from "@/lib/i18n/locales";
import { sessionOpenNow, tradingDayStarted } from "@/lib/session";

const REFRESH_MS = 120_000;
/** Same limit as the other live parts: a forgotten tab stops asking after a while. */
const STOP_AFTER_MS = 60 * 60_000;

/** previousClose: the last 15-minute close of the previous trading day, from the same series. */
type Live = { value: number; time: number; previousClose: number | null };

const jakartaDay = (epochSeconds: number) => new Date((epochSeconds + 7 * 3600) * 1000).toISOString().slice(0, 10);

/**
 * IHSG value on the dashboard: the last end-of-day close from the server, replaced during the session
 * by the latest 15-minute candle (Yahoo, delayed) and compared with that close.
 */
export function IhsgLive({
  locale,
  close,
  change,
  changePercent,
  closeDate,
  labels,
  align = "left",
}: {
  align?: "left" | "right";
  locale: Locale;
  close: number | null;
  change: number | null;
  changePercent: number | null;
  /** The end-of-day close's trading date, "YYYY-MM-DD". */
  closeDate: string | null;
  labels: { live: string; delayed: string; closed: string };
}) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const [live, setLive] = useState<Live | null>(null);

  useEffect(() => {
    let cancelled = false;
    const startedAt = Date.now();
    const load = () => {
      // During the session, and after the close until the evening import has today's close.
      if (!tradingDayStarted() || document.visibilityState !== "visible") return;
      fetch(`/api/candles?symbol=COMPOSITE&interval=15m&range=5D`, { cache: "no-store" })
        .then((r) => (r.ok ? (r.json() as Promise<CandleSeries>) : null))
        .then((series) => {
          const candles = series?.candles ?? [];
          const last = candles.at(-1);
          if (cancelled || !last) return;
          // Only a candle from after the stored close counts as today's value.
          const day = jakartaDay(last.time);
          if (closeDate != null && day <= closeDate) return;
          // The stored close can lag a day behind, so compare with the series' own previous day.
          const previous = candles.findLast((c) => jakartaDay(c.time) < day);
          setLive({ value: last.close, time: last.time, previousClose: previous?.close ?? null });
        })
        .catch(() => {});
    };
    load();
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > STOP_AFTER_MS) window.clearInterval(timer);
      else load();
    }, REFRESH_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [closeDate]);

  const value = live ? live.value : close;
  const reference = live ? (live.previousClose ?? close) : null;
  const shownChange = live ? (reference != null ? live.value - reference : null) : change;
  const shownPercent = live ? (reference ? ((live.value - reference) / reference) * 100 : null) : changePercent;

  return (
    <div className={align === "right" ? "text-right" : "mt-2"}>
      <div className={`flex flex-wrap items-baseline gap-x-3 ${align === "right" ? "justify-end" : ""}`}>
        <p className="text-3xl font-bold tabular-nums">{f.index(value)}</p>
        <p className={`tabular-nums ${changeColor(shownChange)}`}>
          {f.change(shownChange, 2)} ({f.percent(shownPercent)})
        </p>
      </div>
      {live && (
        <p className="mt-1 text-xs">
          {sessionOpenNow() ? <span className="font-semibold text-up">● {labels.live}</span> : <span className="font-semibold text-muted">{labels.closed}</span>}{" "}
          <span className="text-faint">{labels.delayed.replace("{time}", f.intradayTime(live.time))}</span>
        </p>
      )}
    </div>
  );
}
