// Shared by the proxy, server and client code, so it must not import server-only modules.
export const LOCALES = ["en", "id"] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = "en";
export const LOCALE_COOKIE = "NEXT_LOCALE";

export function hasLocale(value: string | undefined): value is Locale {
  return value != null && (LOCALES as readonly string[]).includes(value);
}
