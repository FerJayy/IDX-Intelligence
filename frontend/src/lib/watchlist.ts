import { cookies } from "next/headers";

// "My watchlist" lives in a cookie: one list per browser, no accounts, nothing stored on the backend.
export const WATCHLIST_COOKIE = "watchlist";
export const WATCHLIST_MAX = 30;
const TICKER = /^[A-Z]{4}$/;

export function parseWatchlist(value: string | undefined): string[] {
  if (!value) return [];
  const tickers = value.split(",").map((t) => t.trim().toUpperCase()).filter((t) => TICKER.test(t));
  return [...new Set(tickers)].slice(0, WATCHLIST_MAX);
}

/** Request-time data: call inside <Suspense>. */
export async function readWatchlist(): Promise<string[]> {
  return parseWatchlist((await cookies()).get(WATCHLIST_COOKIE)?.value);
}

export function isTicker(value: string): boolean {
  return TICKER.test(value);
}
