import Link from "next/link";
import type { StockQuote } from "@/lib/api";
import { changeColor, makeFormat } from "@/lib/format";
import { dictionaryFor } from "@/lib/i18n";
import type { Locale } from "@/lib/i18n/locales";

type StockListProps = {
  locale: Locale;
  title: string;
  stocks: StockQuote[];
  // Which figure to show in the last column.
  metric: "changePercent" | "value";
};

export function StockList({ locale, title, stocks, metric }: StockListProps) {
  const t = dictionaryFor(locale);
  const f = makeFormat(locale);
  return (
    <section className="rounded-xl border border-line bg-card p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{title}</h2>

      {stocks.length === 0 ? (
        <p className="mt-4 text-sm text-faint">{t.overview.noStocks}</p>
      ) : (
        <table className="mt-3 w-full text-sm">
          <thead className="text-left text-xs text-faint">
            <tr>
              <th className="py-1 font-medium">{t.overview.ticker}</th>
              <th className="py-1 text-right font-medium">{t.overview.last}</th>
              <th className="py-1 text-right font-medium">
                {metric === "value" ? t.overview.estValue : t.overview.change}
              </th>
            </tr>
          </thead>
          <tbody>
            {stocks.map((stock) => (
              <tr key={stock.ticker} className="border-t border-line">
                <td className="py-2">
                  <Link href={`/${locale}/stocks/${stock.ticker}`} className="group block">
                    <div className="font-mono font-semibold group-hover:text-accent group-hover:underline">
                      {stock.ticker}
                    </div>
                    <div className="truncate text-xs text-faint">{stock.name}</div>
                  </Link>
                </td>
                <td className="py-2 text-right tabular-nums">{f.price(stock.lastPrice)}</td>
                <td
                  className={`py-2 text-right tabular-nums ${
                    metric === "value" ? "" : changeColor(stock.changePercent)
                  }`}
                >
                  {metric === "value" ? f.compact(stock.value) : f.percent(stock.changePercent)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
