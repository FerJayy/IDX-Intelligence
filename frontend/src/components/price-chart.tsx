"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  HistogramSeries,
  type ISeriesApi,
  LineSeries,
  type MouseEventParams,
  type Time,
  type UTCTimestamp,
} from "lightweight-charts";
import type { Candle, CandleSeries, DailyBrokerFlow } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import type { ClientLabels, Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";
import { isLiveTrading, useLiveQuote } from "@/lib/live-store";

// Stable default: a fresh [] each render would re-run the chart effect on every hover.
const NO_FLOWS: DailyBrokerFlow[] = [];
// The chart library labels its time axis in UTC; shifting intraday times by +7 h makes it read WIB.
const WIB_OFFSET_SECONDS = 7 * 3600;
const LOT_SIZE = 100;
/** Intraday candles come from Yahoo (delayed); the backend refreshes them at most every 2 minutes. */
const INTRADAY_REFRESH_MS = 120_000;
/** Same limit as the live price: stop after 15 minutes so a forgotten tab stays quiet. */
const STOP_AFTER_MS = 15 * 60_000;

// Re-render when the theme class on <html> changes, so the chart picks up the new colours.
function subscribeToTheme(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  return () => observer.disconnect();
}
const isDarkTheme = () => document.documentElement.classList.contains("dark");

/** Chart colours come from the theme tokens in globals.css (validated up/down pairs per theme). */
function themeColors() {
  const css = getComputedStyle(document.documentElement);
  const read = (name: string) => css.getPropertyValue(name).trim();
  return {
    up: read("--chart-up"),
    down: read("--chart-down"),
    volume: read("--chart-volume"),
    grid: read("--chart-grid"),
    text: read("--chart-text"),
    foreign: read("--chart-foreign"),
  };
}

/** Daily+ candles become business days ("2026-10-23"); intraday ones a WIB-shifted timestamp. */
function chartTime(candle: Candle, intraday: boolean): Time {
  return intraday
    ? ((candle.time + WIB_OFFSET_SECONDS) as UTCTimestamp)
    : new Date(candle.time * 1000).toISOString().slice(0, 10);
}

type Props = {
  locale: Locale;
  labels: ClientLabels<Dictionary["chart"]>;
  candles: Candle[];
  intraday: boolean;
  /** Daily broker flow, matched to daily candles by date. Ignored for other intervals. */
  flows?: DailyBrokerFlow[];
  /**
   * Keeps the chart moving while the market trades: on daily candles today's candle follows the live
   * price (stocks only); intraday charts re-fetch their candles every 2 minutes.
   */
  live?: { symbol: string; interval: string; range: string; stock: boolean };
};

/** Weekday 09:00-16:15 WIB (holidays aren't known here; a closed day just returns the same candles). */
function sessionOpen(): boolean {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Jakarta", weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false,
  }).formatToParts(new Date());
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  const minutes = Number(get("hour")) * 60 + Number(get("minute"));
  return !["Sat", "Sun"].includes(get("weekday")) && minutes >= 9 * 60 && minutes < 16 * 60 + 15;
}

/** Today's trading date in Jakarta as epoch seconds at 00:00 UTC, the time daily candles use. */
function jakartaTodayTime(): number {
  const day = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta" }).format(new Date());
  return Date.parse(`${day}T00:00:00Z`) / 1000;
}

// Each measure gets its own pane and scale, sharing only the time axis.
// Up candles are hollow and down candles filled, so direction never relies on colour alone.
export function PriceChart({ locale, labels, candles: initialCandles, intraday, flows = NO_FLOWS, live }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  // The drawn series, so live updates can change the last candle without rebuilding the chart.
  const seriesRef = useRef<{ price: ISeriesApi<"Candlestick">; volume: ISeriesApi<"Histogram"> } | null>(null);
  // Candles shown in the readout: the server's, plus whatever live updates added or changed.
  const [extra, setExtra] = useState<{ base: Candle[]; candles: Candle[] } | null>(null);
  const candles = extra?.base === initialCandles ? extra.candles : initialCandles;
  const [hovered, setHovered] = useState<number | null>(null);
  const dark = useSyncExternalStore(subscribeToTheme, isDarkTheme, () => false);
  const f = useMemo(() => makeFormat(locale), [locale]);
  const flowByDate = useMemo(() => new Map(flows.map((x) => [x.date, x])), [flows]);
  const showFlows = !intraday && flows.length > 0;

  useEffect(() => {
    const container = containerRef.current;
    if (!container || initialCandles.length === 0) return;
    const candles = initialCandles;
    const c = themeColors();
    const times = candles.map((candle) => chartTime(candle, intraday));

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: c.text,
        panes: { separatorColor: c.grid },
      },
      grid: { vertLines: { visible: false }, horzLines: { color: c.grid } },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false, timeVisible: intraday, secondsVisible: false },
      localization: { locale: locale === "id" ? "id-ID" : "en-US" },
    });

    const series = chart.addSeries(CandlestickSeries, {
      priceFormat: { type: "custom", formatter: (p: number) => f.price(p), minMove: 1 },
      upColor: "transparent",
      borderUpColor: c.up,
      wickUpColor: c.up,
      downColor: c.down,
      borderDownColor: c.down,
      wickDownColor: c.down,
      priceLineVisible: false,
    });
    series.setData(
      // A missing open/high/low is drawn at the close; the readout still shows it as missing.
      candles.map((b, i) => ({
        time: times[i],
        open: b.open ?? b.close,
        high: b.high ?? Math.max(b.open ?? b.close, b.close),
        low: b.low ?? Math.min(b.open ?? b.close, b.close),
        close: b.close,
      })),
    );

    // Volume lives in its own pane with its own scale - never a second axis on the price chart.
    const volume = chart.addSeries(
      HistogramSeries,
      { color: c.volume, priceFormat: { type: "volume" }, priceLineVisible: false, lastValueVisible: false },
      1,
    );
    volume.setData(
      candles.flatMap((b, i) => (b.volume == null ? [] : [{ time: times[i], value: b.volume }])),
    );
    chart.panes()[1]?.setStretchFactor(0.25);

    if (showFlows) {
      const dates = times as string[];
      // Bars above zero = buying concentrated in the top 5 brokers; below = selling.
      const concentration = chart.addSeries(
        HistogramSeries,
        {
          title: labels.concentrationSeries,
          priceFormat: { type: "custom", formatter: (v: number) => f.percent(v), minMove: 0.1 },
          priceLineVisible: false,
        },
        2,
      );
      concentration.setData(
        dates.flatMap((date) => {
          const flow = flowByDate.get(date);
          return flow
            ? [{ time: date, value: flow.concentration * 100, color: flow.concentration >= 0 ? c.up : c.down }]
            : [];
        }),
      );

      const foreign = chart.addSeries(
        LineSeries,
        {
          title: labels.foreignSeries,
          color: c.foreign,
          lineWidth: 2,
          priceFormat: { type: "custom", formatter: (v: number) => f.signedRupiah(v), minMove: 1 },
          priceLineVisible: false,
        },
        3,
      );
      // Days without foreign data become gaps in the line instead of zeros.
      foreign.setData(
        dates.map((date) => {
          const value = flowByDate.get(date)?.cumulativeForeignNetValue;
          return value == null ? { time: date } : { time: date, value };
        }),
      );
      chart.panes()[2]?.setStretchFactor(0.3);
      chart.panes()[3]?.setStretchFactor(0.3);
    }
    chart.timeScale().fitContent();

    const indexByTime = new Map(times.map((t, i) => [String(t), i]));
    const onMove = (param: MouseEventParams) => {
      const index = param.time == null ? undefined : indexByTime.get(String(param.time));
      setHovered(index ?? null);
    };
    chart.subscribeCrosshairMove(onMove);
    seriesRef.current = { price: series, volume };

    return () => {
      seriesRef.current = null;
      chart.unsubscribeCrosshairMove(onMove);
      chart.remove();
    };
  }, [initialCandles, intraday, flowByDate, showFlows, dark, f, labels, locale]);

  /** Draws changed or new candles at the end of the chart and keeps them for the readout. */
  const applyCandles = useCallback(
    (current: Candle[], updates: Candle[]) => {
      if (updates.length === 0) return;
      const firstTime = updates[0].time;
      // Only the tail can change: earlier candles are final.
      if (current.length > 0 && firstTime < current[current.length - 1].time - 1) {
        const lastTime = current[current.length - 1].time;
        updates = updates.filter((u) => u.time >= lastTime);
        if (updates.length === 0) return;
      }
      const from = updates[0].time;
      const drawn = seriesRef.current;
      for (const u of updates) {
        const time = chartTime(u, intraday);
        drawn?.price.update({
          time,
          open: u.open ?? u.close,
          high: u.high ?? Math.max(u.open ?? u.close, u.close),
          low: u.low ?? Math.min(u.open ?? u.close, u.close),
          close: u.close,
        });
        if (u.volume != null) drawn?.volume.update({ time, value: u.volume });
      }
      setExtra({ base: initialCandles, candles: [...current.filter((x) => x.time < from), ...updates] });
    },
    [initialCandles, intraday],
  );

  // Daily chart of a stock: today's candle follows the live price (already polled for the live box).
  const liveDaily = live?.stock && live.interval === "1d" ? live.symbol : null;
  // Stocks: the shared live poller tells whether the market is trading. The IHSG has no live quote,
  // so its intraday refresh checks the session clock instead.
  const liveState = useLiveQuote(live?.stock ? live.symbol : null);
  const quote = isLiveTrading(liveState) ? liveState.result!.quote! : null;
  const trading = quote != null;
  const candlesRef = useRef(candles);
  useEffect(() => {
    candlesRef.current = candles;
  }, [candles]);
  useEffect(() => {
    if (!liveDaily || !quote || quote.lastPrice == null) return;
    const current = candlesRef.current;
    const todayTime = jakartaTodayTime();
    const last = current[current.length - 1];
    if (last != null && last.time > todayTime) return;
    const price = quote.lastPrice;
    const sameDay = last != null && last.time === todayTime;
    applyCandles(current, [
      {
        time: todayTime,
        open: sameDay ? last.open : price,
        high: Math.max(sameDay ? (last.high ?? price) : price, price),
        low: Math.min(sameDay ? (last.low ?? price) : price, price),
        close: price,
        volume: quote.lot == null ? null : quote.lot * LOT_SIZE,
      },
    ]);
  }, [quote, liveDaily, applyCandles]);

  // Intraday charts: re-fetch the (delayed) candles every 2 minutes while the market trades.
  const refreshSymbol = live != null && intraday && (trading || !live.stock) ? live.symbol : null;
  const refreshInterval = live?.interval;
  const refreshRange = live?.range;
  useEffect(() => {
    if (!refreshSymbol || !refreshInterval || !refreshRange) return;
    const startedAt = Date.now();
    let cancelled = false;
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > STOP_AFTER_MS) {
        window.clearInterval(timer);
        return;
      }
      if (document.visibilityState !== "visible" || !sessionOpen()) return;
      const params = new URLSearchParams({ symbol: refreshSymbol, interval: refreshInterval, range: refreshRange });
      fetch(`/api/candles?${params}`, { cache: "no-store" })
        .then((response) => (response.ok ? (response.json() as Promise<CandleSeries>) : null))
        .then((series) => {
          if (cancelled || !series || series.candles.length === 0) return;
          // The candle that was still forming, plus any new ones.
          applyCandles(candlesRef.current, series.candles.slice(-3));
        })
        .catch(() => {});
    }, INTRADAY_REFRESH_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [refreshSymbol, refreshInterval, refreshRange, applyCandles]);

  if (candles.length === 0) {
    return <p className="text-sm text-faint">{labels.noHistory}</p>;
  }

  // Readout shows the hovered candle, or the latest one when the pointer is elsewhere.
  const index = hovered ?? candles.length - 1;
  const candle = candles[index];
  const previousClose = index > 0 ? candles[index - 1].close : null;
  const changePercent = previousClose == null ? null : ((candle.close - previousClose) / previousClose) * 100;
  const flow = showFlows ? flowByDate.get(chartTime(candle, false) as string) : undefined;

  return (
    <div>
      <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm tabular-nums">
        <Readout label={labels.date} value={intraday ? f.intradayTime(candle.time) : f.candleDate(candle.time)} />
        <Readout label={labels.open} value={f.price(candle.open)} />
        <Readout label={labels.high} value={f.price(candle.high)} />
        <Readout label={labels.low} value={f.price(candle.low)} />
        <Readout label={labels.close} value={f.price(candle.close)} />
        <Readout label={labels.change} value={f.percent(changePercent)} className={changeColor(changePercent)} />
        <Readout label={labels.volume} value={f.compact(candle.volume)} />
        {flow && (
          <>
            <Readout
              label={labels.concentration}
              value={f.percent(flow.concentration * 100)}
              className={changeColor(flow.concentration)}
            />
            <Readout label={labels.buyersSellers} value={`${flow.buyerCount}/${flow.sellerCount}`} />
            <Readout label={labels.foreignNet} value={f.signedRupiah(flow.foreignNetValue)} />
          </>
        )}
      </dl>
      <div ref={containerRef} className={`mt-3 w-full ${showFlows ? "h-[32rem] sm:h-[40rem]" : "h-80 sm:h-[28rem]"}`} />
    </div>
  );
}

function Readout({ label, value, className = "text-fg" }: { label: string; value: string; className?: string }) {
  return (
    <div className="flex gap-1">
      <dt className="text-faint">{label}</dt>
      <dd className={`font-medium ${className}`}>{value}</dd>
    </div>
  );
}
