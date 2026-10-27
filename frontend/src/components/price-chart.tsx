"use client";

import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  HistogramSeries,
  LineSeries,
  type MouseEventParams,
  type Time,
  type UTCTimestamp,
} from "lightweight-charts";
import type { Candle, DailyBrokerFlow } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";

// Stable default: a fresh [] each render would re-run the chart effect on every hover.
const NO_FLOWS: DailyBrokerFlow[] = [];
// The chart library labels its time axis in UTC; shifting intraday times by +7 h makes it read WIB.
const WIB_OFFSET_SECONDS = 7 * 3600;

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
  labels: Dictionary["chart"];
  candles: Candle[];
  intraday: boolean;
  /** Daily broker flow, matched to daily candles by date. Ignored for other intervals. */
  flows?: DailyBrokerFlow[];
};

// Each measure gets its own pane and scale, sharing only the time axis.
// Up candles are hollow and down candles filled, so direction never relies on colour alone.
export function PriceChart({ locale, labels, candles, intraday, flows = NO_FLOWS }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const dark = useSyncExternalStore(subscribeToTheme, isDarkTheme, () => false);
  const f = useMemo(() => makeFormat(locale), [locale]);
  const flowByDate = useMemo(() => new Map(flows.map((x) => [x.date, x])), [flows]);
  const showFlows = !intraday && flows.length > 0;

  useEffect(() => {
    const container = containerRef.current;
    if (!container || candles.length === 0) return;
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

    return () => {
      chart.unsubscribeCrosshairMove(onMove);
      chart.remove();
    };
  }, [candles, intraday, flowByDate, showFlows, dark, f, labels, locale]);

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
