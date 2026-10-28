"use server";

import { refresh } from "next/cache";
import { cookies, headers } from "next/headers";
import { redirect, unstable_rethrow } from "next/navigation";
import {
  changeMyWatchlist,
  loginAccount,
  logoutAccount,
  mergeMyWatchlist,
  registerAccount,
  requestPasswordReset,
  resetPassword,
  SESSION_COOKIE,
  trackStock,
  USER_COOKIE,
  type AuthResult,
  type TrackOutcome,
} from "@/lib/api";
import { hasLocale, type Locale } from "@/lib/i18n/locales";
import { isTicker, OLD_WATCHLIST_COOKIE, parseOldWatchlist } from "@/lib/watchlist";

// Server Actions are reachable by any POST, so every input is checked here before use.

function locale(formData: FormData): Locale {
  const lang = String(formData.get("lang") ?? "");
  if (!hasLocale(lang)) throw new Error("Invalid request");
  return lang;
}

/** Only paths inside this site, so a crafted link can't send people elsewhere after login. */
function safeNext(value: FormDataEntryValue | null, lang: Locale): string {
  const next = String(value ?? "");
  return /^\/(en|id)(\/[A-Za-z0-9/_\-?=&.%]*)?$/.test(next) && !next.startsWith("//") ? next : `/${lang}`;
}

async function clientIp(): Promise<string | null> {
  const h = await headers();
  return h.get("x-forwarded-for")?.split(",")[0].trim() || h.get("x-real-ip");
}

async function startSession(result: Extract<AuthResult, { ok: true }>) {
  const store = await cookies();
  const options = {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    expires: new Date(result.expiresAt),
  };
  store.set(SESSION_COOKIE, result.token, options);
  store.set(USER_COOKIE, result.email, options);
  // A list starred before accounts existed moves into the account once.
  const old = parseOldWatchlist(store.get(OLD_WATCHLIST_COOKIE)?.value);
  if (old.length > 0) {
    await mergeMyWatchlist(old, result.token).catch((error) => console.error(error));
    store.delete(OLD_WATCHLIST_COOKIE);
  }
}

export async function login(formData: FormData) {
  const lang = locale(formData);
  const next = safeNext(formData.get("next"), lang);
  const result = await loginAccount(String(formData.get("email") ?? ""), String(formData.get("password") ?? ""), await clientIp());
  if (!result.ok) redirect(`/${lang}/login?error=${encodeURIComponent(result.error)}&next=${encodeURIComponent(next)}`);
  await startSession(result);
  redirect(next);
}

export async function register(formData: FormData) {
  const lang = locale(formData);
  const password = String(formData.get("password") ?? "");
  if (password !== String(formData.get("confirm") ?? "")) redirect(`/${lang}/register?error=PASSWORD_MISMATCH`);
  const result = await registerAccount(String(formData.get("email") ?? ""), password, await clientIp());
  if (!result.ok) redirect(`/${lang}/register?error=${encodeURIComponent(result.error)}`);
  await startSession(result);
  redirect(`/${lang}`);
}

export async function logout(formData: FormData) {
  const lang = locale(formData);
  await logoutAccount();
  const store = await cookies();
  store.delete(SESSION_COOKIE);
  store.delete(USER_COOKIE);
  redirect(`/${lang}/login`);
}

export async function forgotPassword(formData: FormData) {
  const lang = locale(formData);
  const error = await requestPasswordReset(String(formData.get("email") ?? ""), lang, await clientIp());
  redirect(error ? `/${lang}/forgot-password?error=${encodeURIComponent(error)}` : `/${lang}/forgot-password?sent=1`);
}

export async function chooseNewPassword(formData: FormData) {
  const lang = locale(formData);
  const token = String(formData.get("token") ?? "");
  const password = String(formData.get("password") ?? "");
  const back = `/${lang}/reset-password?token=${encodeURIComponent(token)}`;
  if (password !== String(formData.get("confirm") ?? "")) redirect(`${back}&error=PASSWORD_MISMATCH`);
  const error = await resetPassword(token, password);
  if (error) redirect(`${back}&error=${encodeURIComponent(error)}`);
  // Every session of the account was ended; this browser's cookie is stale too.
  const store = await cookies();
  store.delete(SESSION_COOKIE);
  store.delete(USER_COOKIE);
  redirect(`/${lang}/login?reset=1`);
}

/** Star button on a stock page and remove buttons on My list: the page re-renders with the new list. */
export async function toggleWatch(formData: FormData) {
  const ticker = String(formData.get("ticker") ?? "").toUpperCase();
  if (!isTicker(ticker)) throw new Error("Invalid request");
  await changeMyWatchlist(ticker, formData.get("remove") !== "1");
  refresh();
}

/** "Add" box on My list. */
export async function addToWatchlist(formData: FormData) {
  const lang = locale(formData);
  const ticker = String(formData.get("ticker") ?? "").trim().toUpperCase();
  const error = isTicker(ticker) ? await changeMyWatchlist(ticker, true) : "UNKNOWN_STOCK";
  const problem = error === "LIST_FULL" ? "full" : error ? "unknown" : null;
  redirect(problem ? `/${lang}/watchlist?problem=${problem}&ticker=${encodeURIComponent(ticker.slice(0, 8))}` : `/${lang}/watchlist`);
}

/**
 * "Load broker data" button. Only accepts a well-formed ticker and locale; the backend enforces the
 * per-day and total tracking limits.
 */
export async function loadBrokerData(formData: FormData) {
  const lang = locale(formData);
  const ticker = String(formData.get("ticker") ?? "").toUpperCase();
  if (!isTicker(ticker)) throw new Error("Invalid request");
  let outcome: TrackOutcome;
  try {
    outcome = await trackStock(ticker);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    outcome = "FAILED";
  }
  redirect(`/${lang}/stocks/${ticker}?tracking=${outcome}`);
}
