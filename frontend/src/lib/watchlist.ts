import { unstable_rethrow } from "next/navigation";
import { getMyWatchlist } from "@/lib/api";

// "My list" is stored per account on the backend, so it follows the user to every device.
export const WATCHLIST_MAX = 30;
/** Before accounts the list lived in this cookie; it is merged into the account on login, then deleted. */
export const OLD_WATCHLIST_COOKIE = "watchlist";
const TICKER = /^[A-Z]{4}$/;

export function parseOldWatchlist(value: string | undefined): string[] {
  if (!value) return [];
  const tickers = value.split(",").map((t) => t.trim().toUpperCase()).filter((t) => TICKER.test(t));
  return [...new Set(tickers)].slice(0, WATCHLIST_MAX);
}

/** The logged-in user's list; request-time data, so call it inside <Suspense>. */
export async function readWatchlist(): Promise<string[]> {
  try {
    return await getMyWatchlist();
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return [];
  }
}

export function isTicker(value: string): boolean {
  return TICKER.test(value);
}
