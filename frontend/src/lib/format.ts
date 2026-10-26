// Indonesian number formatting: "9.850", "+0,77%", "841,2 M" (miliar).
// Missing values render as an em dash so they are never mistaken for zero.
const MISSING = "—";

const integer = new Intl.NumberFormat("id-ID", { maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat("id-ID", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const compact = new Intl.NumberFormat("id-ID", {
  notation: "compact",
  maximumFractionDigits: 1,
});
const jakartaDateTime = new Intl.DateTimeFormat("id-ID", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Jakarta",
});

export function formatPrice(value: number | null): string {
  return value == null ? MISSING : integer.format(value);
}

export function formatIndex(value: number | null): string {
  return value == null ? MISSING : decimal.format(value);
}

export function formatChange(value: number | null, fractionDigits = 0): string {
  if (value == null) return MISSING;
  const formatted = fractionDigits ? decimal.format(value) : integer.format(value);
  return value > 0 ? `+${formatted}` : formatted;
}

export function formatPercent(value: number | null): string {
  if (value == null) return MISSING;
  return `${value > 0 ? "+" : ""}${decimal.format(value)}%`;
}

export function formatCompact(value: number | null): string {
  return value == null ? MISSING : compact.format(value);
}

export function formatJakartaTime(iso: string): string {
  return `${jakartaDateTime.format(new Date(iso))} WIB`;
}

const tradingDay = new Intl.DateTimeFormat("id-ID", { dateStyle: "medium", timeZone: "UTC" });

/** Formats a "YYYY-MM-DD" trading day without shifting it across time zones. */
export function formatTradingDate(date: string): string {
  return tradingDay.format(new Date(`${date}T00:00:00Z`));
}

export function changeColor(value: number | null): string {
  if (value == null || value === 0) return "text-slate-400";
  return value > 0 ? "text-emerald-400" : "text-red-400";
}

const LOT_SIZE = 100;

/** IDX volume in lots (1 lot = 100 shares), compact: "1,2 jt lot". */
export function formatLots(shares: number | null): string {
  return shares == null ? MISSING : `${compact.format(shares / LOT_SIZE)} lot`;
}

/** Signed compact rupiah: "+Rp1,9 T", "-Rp806 M". */
export function formatSignedRupiah(value: number | null): string {
  if (value == null) return MISSING;
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  return `${sign}Rp${compact.format(Math.abs(value))}`;
}
