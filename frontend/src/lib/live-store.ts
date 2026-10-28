"use client";

import { useCallback, useSyncExternalStore } from "react";
import type { LiveResult } from "@/lib/api";

// One poller per ticker, shared by every component on the page (live box, header price, chart),
// so the page spends one provider request per minute however many places show the price.

const REFRESH_MS = 60_000;
/** Stop polling after this long so a forgotten tab can't use up the free daily limit. */
const STOP_AFTER_MS = 15 * 60_000;

const UNAVAILABLE: LiveResult = { status: "UNAVAILABLE", quote: null, fetchedAt: null, budgetLeft: 0 };

export type LiveState = { result: LiveResult | null; paused: boolean };

type Entry = {
  state: LiveState;
  listeners: Set<() => void>;
  timer?: number;
  startedAt: number;
};

const entries = new Map<string, Entry>();
const INITIAL: LiveState = { result: null, paused: false };

function entry(ticker: string): Entry {
  let e = entries.get(ticker);
  if (!e) {
    e = { state: INITIAL, listeners: new Set(), startedAt: Date.now() };
    entries.set(ticker, e);
  }
  return e;
}

function set(ticker: string, next: Partial<LiveState>) {
  const e = entry(ticker);
  e.state = { ...e.state, ...next };
  e.listeners.forEach((l) => l());
}

/** Nothing more to ask once the market is closed or the budget is spent. */
function finished(state: LiveState) {
  const status = state.result?.status;
  return state.paused || status === "MARKET_CLOSED" || status === "BUDGET_USED" || status === "UNAVAILABLE";
}

function tick(ticker: string) {
  fetch(`/api/live/${ticker}`, { cache: "no-store" })
    .then((response) => response.json() as Promise<LiveResult>)
    .catch(() => UNAVAILABLE)
    .then((result) => {
      set(ticker, { result });
      if (finished(entry(ticker).state)) stop(ticker);
    });
}

function start(ticker: string) {
  const e = entry(ticker);
  if (e.timer != null || finished(e.state)) return;
  e.startedAt = Date.now();
  tick(ticker);
  e.timer = window.setInterval(() => {
    if (Date.now() - e.startedAt > STOP_AFTER_MS) {
      stop(ticker);
      set(ticker, { paused: true });
      return;
    }
    // Background tabs don't spend requests; the next visible tick catches up.
    if (document.visibilityState === "visible") tick(ticker);
  }, REFRESH_MS);
}

function stop(ticker: string) {
  const e = entries.get(ticker);
  if (e?.timer != null) {
    window.clearInterval(e.timer);
    e.timer = undefined;
  }
}

/** Starts polling while at least one component shows this ticker. */
export function useLiveQuote(ticker: string | null): LiveState & { resume: () => void } {
  const subscribe = useCallback(
    (listener: () => void) => {
      if (ticker == null) return () => {};
      const e = entry(ticker);
      e.listeners.add(listener);
      start(ticker);
      return () => {
        e.listeners.delete(listener);
        if (e.listeners.size === 0) stop(ticker);
      };
    },
    [ticker],
  );
  const state = useSyncExternalStore(subscribe, () => (ticker == null ? INITIAL : entry(ticker).state), () => INITIAL);
  const resume = useCallback(() => {
    if (ticker == null) return;
    set(ticker, { paused: false });
    start(ticker);
  }, [ticker]);
  return { ...state, resume };
}

/** True while the market session is running and the quote is real-time (not a no-trade placeholder). */
export function isLiveTrading(state: LiveState) {
  const q = state.result?.quote;
  return state.result?.status === "LIVE" && q != null && !q.noTradeToday && q.lastPrice != null;
}
