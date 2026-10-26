import { lang } from "next/root-params";
import { notFound } from "next/navigation";
import { makeFormat } from "@/lib/format";
import { en, type Dictionary } from "./en";
import { id } from "./id";
import { hasLocale, type Locale } from "./locales";

// Server-only (next/root-params fails the build if imported into a Client Component).
const DICTIONARIES: Record<Locale, Dictionary> = { en, id };

/** The current request's locale, from the /[lang] segment. Unknown locales 404. */
export async function getLocale(): Promise<Locale> {
  const locale = await lang();
  if (!hasLocale(locale)) notFound();
  return locale;
}

/** Dictionary and formatters for the current locale. */
export async function getI18n() {
  const locale = await getLocale();
  return { locale, t: DICTIONARIES[locale], f: makeFormat(locale) };
}

/** For Client Components, which receive the locale as a prop. */
export function dictionaryFor(locale: Locale): Dictionary {
  return DICTIONARIES[locale];
}
