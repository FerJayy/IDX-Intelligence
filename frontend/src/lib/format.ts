import type { Locale } from "@/lib/i18n/locales";

// Number and date formatting that follows the selected language:
//   id: 9.850 · +0,77% · Rp841,2 M (miliar) · 26 Okt 2026
//   en: 9,850 · +0.77% · Rp841.2B (billion)  · Oct 26, 2026
// Missing values render as an em dash so they are never mistaken for zero.
const MISSING = "—";
const LOT_SIZE = 100;

const INTL_LOCALE: Record<Locale, string> = { id: "id-ID", en: "en-US" };

export type Format = ReturnType<typeof makeFormat>;

export function makeFormat(locale: Locale) {
  const tag = INTL_LOCALE[locale];
  const integer = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const decimal = new Intl.NumberFormat(tag, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const oneDecimal = new Intl.NumberFormat(tag, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const compact = new Intl.NumberFormat(tag, { notation: "compact", maximumFractionDigits: 1 });
  const dateTime = new Intl.DateTimeFormat(tag, { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Jakarta" });
  const tradingDay = new Intl.DateTimeFormat(tag, { dateStyle: "medium", timeZone: "UTC" });
  const tradingDayLong = new Intl.DateTimeFormat(tag, { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });

  const sign = (value: number) => (value > 0 ? "+" : "");

  return {
    price: (value: number | null | undefined) => (value == null ? MISSING : integer.format(value)),
    index: (value: number | null | undefined) => (value == null ? MISSING : decimal.format(value)),
    integer: (value: number) => integer.format(value),
    decimal1: (value: number) => oneDecimal.format(value),
    change: (value: number | null | undefined, fractionDigits = 0) =>
      value == null ? MISSING : sign(value) + (fractionDigits ? decimal.format(value) : integer.format(value)),
    /** Value already in percent units: 0.77 -> "+0,77%". */
    percent: (value: number | null | undefined) =>
      value == null ? MISSING : `${sign(value)}${decimal.format(value)}%`,
    /** Fraction: 0.061 -> "6,1%". */
    fraction: (value: number | null | undefined) =>
      value == null ? MISSING : `${oneDecimal.format(value * 100)}%`,
    /** Signed fraction: 0.061 -> "+6,1%". */
    signedFraction: (value: number | null | undefined) =>
      value == null ? MISSING : `${sign(value)}${oneDecimal.format(value * 100)}%`,
    compact: (value: number | null | undefined) => (value == null ? MISSING : compact.format(value)),
    rupiah: (value: number | null | undefined) => (value == null ? MISSING : `Rp${compact.format(value)}`),
    signedRupiah: (value: number | null | undefined) => {
      if (value == null) return MISSING;
      const s = value > 0 ? "+" : value < 0 ? "-" : "";
      return `${s}Rp${compact.format(Math.abs(value))}`;
    },
    /** IDX volume in lots (1 lot = 100 shares). */
    lots: (shares: number | null | undefined) =>
      shares == null ? MISSING : `${compact.format(shares / LOT_SIZE)} lot`,
    jakartaTime: (iso: string) => `${dateTime.format(new Date(iso))} WIB`,
    /** "YYYY-MM-DD" trading day, without shifting it across time zones. */
    tradingDate: (date: string) => tradingDay.format(new Date(`${date}T00:00:00Z`)),
    tradingDateLong: (date: string) => tradingDayLong.format(new Date(`${date}T00:00:00Z`)),
  };
}

export function changeColor(value: number | null | undefined): string {
  if (value == null || value === 0) return "text-muted";
  return value > 0 ? "text-up" : "text-down";
}
