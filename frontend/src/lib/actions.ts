"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getStock, trackStock, type TrackOutcome } from "@/lib/api";
import { hasLocale } from "@/lib/i18n/locales";
import { isTicker, readWatchlist, WATCHLIST_COOKIE, WATCHLIST_MAX } from "@/lib/watchlist";

async function saveWatchlist(tickers: string[]) {
  (await cookies()).set(WATCHLIST_COOKIE, tickers.join(","), {
    maxAge: 60 * 60 * 24 * 365 * 2,
    sameSite: "lax",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
  });
}

/** Star button on a stock page and remove buttons on the watchlist: the page re-renders with the new list. */
export async function toggleWatch(formData: FormData) {
  const ticker = String(formData.get("ticker") ?? "").toUpperCase();
  if (!isTicker(ticker)) throw new Error("Invalid request");
  const list = await readWatchlist();
  if (list.includes(ticker)) {
    await saveWatchlist(list.filter((t) => t !== ticker));
  } else if (list.length < WATCHLIST_MAX) {
    await saveWatchlist([...list, ticker]);
  }
}

/** "Add" box on the watchlist page; only adds stocks the backend knows. */
export async function addToWatchlist(formData: FormData) {
  const ticker = String(formData.get("ticker") ?? "").trim().toUpperCase();
  const lang = String(formData.get("lang") ?? "");
  if (!hasLocale(lang)) throw new Error("Invalid request");
  const list = await readWatchlist();
  let problem: "unknown" | "full" | null = null;
  if (list.length >= WATCHLIST_MAX) {
    problem = "full";
  } else if (!list.includes(ticker)) {
    let known = false;
    if (isTicker(ticker)) {
      try {
        known = (await getStock(ticker)) != null;
      } catch (error) {
        console.error(error);
      }
    }
    if (known) await saveWatchlist([...list, ticker]);
    else problem = "unknown";
  }
  redirect(problem ? `/${lang}/watchlist?problem=${problem}&ticker=${encodeURIComponent(ticker.slice(0, 8))}` : `/${lang}/watchlist`);
}

/**
 * "Load broker data" button. Reachable by any POST, so it only accepts a well-formed ticker and
 * locale; the backend enforces the per-day and total tracking limits.
 */
export async function loadBrokerData(formData: FormData) {
  const ticker = String(formData.get("ticker") ?? "").toUpperCase();
  const lang = String(formData.get("lang") ?? "");
  if (!/^[A-Z]{4}$/.test(ticker) || !hasLocale(lang)) {
    throw new Error("Invalid request");
  }
  let outcome: TrackOutcome;
  try {
    outcome = await trackStock(ticker);
  } catch (error) {
    console.error(error);
    outcome = "FAILED";
  }
  redirect(`/${lang}/stocks/${ticker}?tracking=${outcome}`);
}
