"use client";

import { useMemo } from "react";
import { changeColor, makeFormat } from "@/lib/format";
import type { Locale } from "@/lib/i18n/locales";
import { isLiveTrading, useLiveQuote } from "@/lib/live-store";

const LOT_SIZE = 100;

/**
 * The big price on a stock page: today's live price while the market is trading (from the same
 * shared poller as the live box), otherwise the last end-of-day close the server rendered.
 */
export function HeaderPrice({
  ticker,
  locale,
  close,
  change,
  changePercent,
  details,
  labels,
}: {
  ticker: string | null;
  locale: Locale;
  close: number | null;
  change: number | null;
  changePercent: number | null;
  /** End-of-day lines under the price (volume, "Close of …"), already formatted on the server. */
  details: string[];
  labels: { live: string; updated: string; volume: string; value: string };
}) {
  const f = useMemo(() => makeFormat(locale), [locale]);
  const state = useLiveQuote(ticker);
  const q = isLiveTrading(state) ? state.result!.quote! : null;

  return (
    <div className="text-right">
      <p className="text-3xl font-bold tabular-nums">{f.price(q ? q.lastPrice : close)}</p>
      <p className={`tabular-nums ${changeColor(q ? q.change : change)}`}>
        {f.change(q ? q.change : change)} ({f.percent(q ? q.changePercent : changePercent)})
      </p>
      {q ? (
        <>
          <p className="text-xs text-faint">
            {labels.volume} {f.lots(q.lot == null ? null : q.lot * LOT_SIZE)} · {labels.value} {f.rupiah(q.value)}
          </p>
          <p className="text-xs font-semibold text-up">
            ● {labels.live}
            {q.dataTime && <span className="font-normal text-faint"> {labels.updated.replace("{time}", q.dataTime)}</span>}
          </p>
        </>
      ) : (
        details.map((line) => (
          <p key={line} className="text-xs text-faint">
            {line}
          </p>
        ))
      )}
    </div>
  );
}
