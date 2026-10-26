"use client";

import { useRouter } from "next/navigation";
import { LOCALE_COOKIE, LOCALES, type Locale } from "@/lib/i18n/locales";

const SHORT: Record<Locale, string> = { en: "EN", id: "ID" };

/** Remembers the choice so the proxy uses it for URLs without a language (e.g. "/"). */
function rememberLocale(locale: Locale) {
  document.cookie = `${LOCALE_COOKIE}=${locale}; path=/; max-age=31536000; samesite=lax`;
}

/** Same page in another language: swaps the /en or /id prefix and keeps ?range=... etc. */
function urlIn(locale: Locale) {
  const { pathname, search } = window.location;
  return `/${locale}${pathname.replace(/^\/(en|id)(?=\/|$)/, "")}${search}`;
}

// The URL is read when clicked rather than during render, so this component doesn't
// make dynamic pages wait for request data (no Suspense boundary needed).
export function LanguageSwitch({
  locale,
  labels,
}: {
  locale: Locale;
  labels: { label: string } & Record<Locale, string>;
}) {
  const router = useRouter();

  function choose(next: Locale) {
    if (next === locale) return;
    rememberLocale(next);
    router.push(urlIn(next), { scroll: false });
  }

  return (
    <div role="radiogroup" aria-label={labels.label} className="flex rounded-md border border-line p-0.5">
      {LOCALES.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={option === locale}
          title={labels[option]}
          onClick={() => choose(option)}
          className={`rounded px-2 py-0.5 text-xs font-semibold ${
            option === locale ? "bg-chip text-fg" : "text-muted hover:text-fg"
          }`}
        >
          {SHORT[option]}
        </button>
      ))}
    </div>
  );
}
