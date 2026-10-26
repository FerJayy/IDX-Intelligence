import Link from "next/link";
import type { StockQuote } from "@/lib/api";
import {
  changeColor,
  formatCompact,
  formatPercent,
  formatPrice,
} from "@/lib/format";

type StockListProps = {
  title: string;
  stocks: StockQuote[];
  // Which figure to show in the last column.
  metric: "changePercent" | "value";
};

export function StockList({ title, stocks, metric }: StockListProps) {
  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
        {title}
      </h2>

      {stocks.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">No stocks to show.</p>
      ) : (
        <table className="mt-3 w-full text-sm">
          <thead className="text-left text-xs text-slate-500">
            <tr>
              <th className="py-1 font-medium">Ticker</th>
              <th className="py-1 text-right font-medium">Last</th>
              <th className="py-1 text-right font-medium">
                {metric === "value" ? "Value (Rp)" : "Change"}
              </th>
            </tr>
          </thead>
          <tbody>
            {stocks.map((stock) => (
              <tr key={stock.ticker} className="border-t border-slate-800">
                <td className="py-2">
                  <Link href={`/stocks/${stock.ticker}`} className="group block">
                    <div className="font-mono font-semibold group-hover:text-sky-300 group-hover:underline">
                      {stock.ticker}
                    </div>
                    <div className="truncate text-xs text-slate-500">
                      {stock.name}
                    </div>
                  </Link>
                </td>
                <td className="py-2 text-right tabular-nums">
                  {formatPrice(stock.lastPrice)}
                </td>
                <td
                  className={`py-2 text-right tabular-nums ${
                    metric === "value" ? "" : changeColor(stock.changePercent)
                  }`}
                >
                  {metric === "value"
                    ? formatCompact(stock.value)
                    : formatPercent(stock.changePercent)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
