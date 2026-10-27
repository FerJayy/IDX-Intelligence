"use server";

import { redirect } from "next/navigation";
import { trackStock, type TrackOutcome } from "@/lib/api";
import { hasLocale } from "@/lib/i18n/locales";

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
