"use client";

import { useEffect, useRef, useState } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  HistogramSeries,
  LineSeries,
  type MouseEventParams,
} from "lightweight-charts";
import type { DailyBrokerFlow, PriceBar } from "@/lib/api";
import {
  changeColor,
  formatCompact,
  formatPercent,
  formatPrice,
  formatSignedRupiah,
  formatTradingDate,
} from "@/lib/format";

// Up/down pair validated for colour-vision deficiency on the slate-950 surface.
// Up candles are hollow and down candles filled, so direction never relies on colour alone.
const UP = "#34d399";
const DOWN = "#ef4444";
const VOLUME = "#475569";
const FOREIGN = "#38bdf8";
const GRID = "#1e293b";
const TEXT = "#94a3b8";

// Stable default: a fresh [] each render would re-run the chart effect on every hover.
const NO_FLOWS: DailyBrokerFlow[] = [];

// Each measure gets its own pane and scale, sharing only the date axis.
export function PriceChart({ bars, flows = NO_FLOWS }: { bars: PriceBar[]; flows?: DailyBrokerFlow[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState<number | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || bars.length === 0) return;

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: TEXT,
        panes: { separatorColor: GRID },
      },
      grid: { vertLines: { visible: false }, horzLines: { color: GRID } },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false },
      localization: { locale: "id-ID" },
    });

    const candles = chart.addSeries(CandlestickSeries, {
      priceFormat: { type: "custom", formatter: (p: number) => formatPrice(p), minMove: 1 },
      upColor: "transparent",
      borderUpColor: UP,
      wickUpColor: UP,
      downColor: DOWN,
      borderDownColor: DOWN,
      wickDownColor: DOWN,
      priceLineVisible: false,
    });
    candles.setData(
      bars.map((b) => ({ time: b.date, open: b.open, high: b.high, low: b.low, close: b.close })),
    );

    // Volume lives in its own pane with its own scale - never a second axis on the price chart.
    const volume = chart.addSeries(
      HistogramSeries,
      { color: VOLUME, priceFormat: { type: "volume" }, priceLineVisible: false, lastValueVisible: false },
      1,
    );
    volume.setData(
      bars.filter((b) => b.volume != null).map((b) => ({ time: b.date, value: b.volume! })),
    );
    chart.panes()[1]?.setStretchFactor(0.25);

    if (flows.length > 0) {
      // Bars above zero = buying concentrated in the top 5 brokers; below = selling.
      const concentration = chart.addSeries(
        HistogramSeries,
        {
          title: "Top-5 conc.",
          priceFormat: { type: "custom", formatter: (v: number) => `${v.toFixed(1)}%`, minMove: 0.1 },
          priceLineVisible: false,
        },
        2,
      );
      concentration.setData(
        flows.map((f) => ({
          time: f.date,
          value: f.concentration * 100,
          color: f.concentration >= 0 ? UP : DOWN,
        })),
      );

      const foreign = chart.addSeries(
        LineSeries,
        {
          title: "Foreign net (cum.)",
          color: FOREIGN,
          lineWidth: 2,
          priceFormat: { type: "custom", formatter: (v: number) => formatSignedRupiah(v), minMove: 1 },
          priceLineVisible: false,
        },
        3,
      );
      foreign.setData(flows.map((f) => ({ time: f.date, value: f.cumulativeForeignNetValue })));
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
  }, [bars, flows]);

  if (bars.length === 0) {
    return <p className="text-sm text-slate-500">No price history available.</p>;
  }

  // Readout shows the hovered day, or the latest day when the pointer is elsewhere.
  const index = hovered ?? bars.length - 1;
  const bar = bars[index];
  const previousClose = index > 0 ? bars[index - 1].close : null;
  const changePercent =
    previousClose == null ? null : ((bar.close - previousClose) / previousClose) * 100;
  const flow = flows.find((f) => f.date === bar.date);

  return (
    <div>
      <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm tabular-nums">
        <Readout label="Date" value={formatTradingDate(bar.date)} />
        <Readout label="O" value={formatPrice(bar.open)} />
        <Readout label="H" value={formatPrice(bar.high)} />
        <Readout label="L" value={formatPrice(bar.low)} />
        <Readout label="C" value={formatPrice(bar.close)} />
        <Readout label="Chg" value={formatPercent(changePercent)} className={changeColor(changePercent)} />
        <Readout label="Vol" value={formatCompact(bar.volume)} />
        {flow && (
          <>
            <Readout
              label="Top-5 conc."
              value={formatPercent(flow.concentration * 100)}
              className={changeColor(flow.concentration)}
            />
            <Readout label="Buyers/Sellers" value={`${flow.buyerCount}/${flow.sellerCount}`} />
            <Readout label="Foreign net" value={formatSignedRupiah(flow.foreignNetValue)} />
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

function Readout({ label, value, className = "text-slate-100" }: { label: string; value: string; className?: string }) {
  return (
    <div className="flex gap-1">
      <dt className="text-slate-500">{label}</dt>
      <dd className={`font-medium ${className}`}>{value}</dd>
    </div>
  );
}
