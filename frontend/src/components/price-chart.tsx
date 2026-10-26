"use client";

import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  HistogramSeries,
  LineSeries,
  type MouseEventParams,
} from "lightweight-charts";
import type { DailyBrokerFlow, PriceBar } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import type { Dictionary } from "@/lib/i18n/en";
import type { Locale } from "@/lib/i18n/locales";

// Stable default: a fresh [] each render would re-run the chart effect on every hover.
const NO_FLOWS: DailyBrokerFlow[] = [];

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

type Props = {
  locale: Locale;
  labels: Dictionary["chart"];
  bars: PriceBar[];
  flows?: DailyBrokerFlow[];
};

// Each measure gets its own pane and scale, sharing only the date axis.
// Up candles are hollow and down candles filled, so direction never relies on colour alone.
export function PriceChart({ locale, labels, bars, flows = NO_FLOWS }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const dark = useSyncExternalStore(subscribeToTheme, isDarkTheme, () => false);
  const f = useMemo(() => makeFormat(locale), [locale]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || bars.length === 0) return;
    const c = themeColors();

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: c.text,
        panes: { separatorColor: c.grid },
      },
      grid: { vertLines: { visible: false }, horzLines: { color: c.grid } },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false },
      localization: { locale: locale === "id" ? "id-ID" : "en-US" },
    });

    const candles = chart.addSeries(CandlestickSeries, {
      priceFormat: { type: "custom", formatter: (p: number) => f.price(p), minMove: 1 },
      upColor: "transparent",
      borderUpColor: c.up,
      wickUpColor: c.up,
      downColor: c.down,
      borderDownColor: c.down,
      wickDownColor: c.down,
      priceLineVisible: false,
    });
    candles.setData(
      // A missing open/high/low is drawn at the close; the readout and table still show it as missing.
      bars.map((b) => ({
        time: b.date,
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
    volume.setData(bars.filter((b) => b.volume != null).map((b) => ({ time: b.date, value: b.volume! })));
    chart.panes()[1]?.setStretchFactor(0.25);

    if (flows.length > 0) {
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
        flows.map((flow) => ({
          time: flow.date,
          value: flow.concentration * 100,
          color: flow.concentration >= 0 ? c.up : c.down,
        })),
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
        flows.map((flow) =>
          flow.cumulativeForeignNetValue == null
            ? { time: flow.date }
            : { time: flow.date, value: flow.cumulativeForeignNetValue },
        ),
      );
      chart.panes()[2]?.setStretchFactor(0.3);
      chart.panes()[3]?.setStretchFactor(0.3);
    }
    chart.timeScale().fitContent();

    const indexByDate = new Map(bars.map((b, i) => [b.date, i]));
    const onMove = (param: MouseEventParams) => {
      const index = typeof param.time === "string" ? indexByDate.get(param.time) : undefined;
      setHovered(index ?? null);
    };
    chart.subscribeCrosshairMove(onMove);

    return () => {
      chart.unsubscribeCrosshairMove(onMove);
      chart.remove();
    };
  }, [bars, flows, dark, f, labels, locale]);

  if (bars.length === 0) {
    return <p className="text-sm text-faint">{labels.noHistory}</p>;
  }

  // Readout shows the hovered day, or the latest day when the pointer is elsewhere.
  const index = hovered ?? bars.length - 1;
  const bar = bars[index];
  const previousClose = index > 0 ? bars[index - 1].close : null;
  const changePercent = previousClose == null ? null : ((bar.close - previousClose) / previousClose) * 100;
  const flow = flows.find((x) => x.date === bar.date);

  return (
    <div>
      <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm tabular-nums">
        <Readout label={labels.date} value={f.tradingDate(bar.date)} />
        <Readout label={labels.open} value={f.price(bar.open)} />
        <Readout label={labels.high} value={f.price(bar.high)} />
        <Readout label={labels.low} value={f.price(bar.low)} />
        <Readout label={labels.close} value={f.price(bar.close)} />
        <Readout label={labels.change} value={f.percent(changePercent)} className={changeColor(changePercent)} />
        <Readout label={labels.volume} value={f.compact(bar.volume)} />
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
      <div
        ref={containerRef}
        className={`mt-3 w-full ${flows.length > 0 ? "h-[32rem] sm:h-[40rem]" : "h-80 sm:h-[28rem]"}`}
      />
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
