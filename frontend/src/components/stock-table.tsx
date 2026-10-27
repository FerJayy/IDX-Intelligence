import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { getStockList, STOCK_SORTS, type StockList, type StockListQuery } from "@/lib/api";
import { changeColor } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import { DemoBanner } from "./demo-banner";

// Server Component. The filter form is a plain GET form: it works without client-side JavaScript
// and every result page has a shareable URL.
export async function StockTable({ query }: { query: StockListQuery }) {
  const { locale, t, f } = await getI18n();
  let list: StockList;
  try {
    list = await getStockList(query);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>
    );
  }

  const pages = Math.max(1, Math.ceil(list.total / list.size));
  const base = `/${locale}/stocks`;
  const pageHref = (page: number) => {
    const params = new URLSearchParams();
    if (query.search) params.set("search", query.search);
    if (query.sector) params.set("sector", query.sector);
    if (query.change) params.set("change", query.change);
    params.set("sort", query.sort ?? "marketCap");
    params.set("order", query.order ?? "desc");
    params.set("page", String(page));
    return `${base}?${params}`;
  };
  const field = "rounded-md border border-line bg-card px-3 py-1.5 text-sm";

  return (
    <div className="space-y-4">
      {list.demo && <DemoBanner notice={t.common.demoNotice} />}

      <form action={base} method="get" className="flex flex-wrap items-end gap-2">
        <input
          type="search"
          name="search"
          defaultValue={query.search}
          placeholder={t.stockList.search}
          aria-label={t.stockList.search}
          className={`${field} min-w-48 flex-1`}
        />
        <select name="sector" defaultValue={query.sector ?? ""} aria-label={t.stockList.sector} className={field}>
          <option value="">{t.stockList.allSectors}</option>
          {list.sectors.map((sector) => (
            <option key={sector} value={sector}>
              {sector}
            </option>
          ))}
        </select>
        <select name="sort" defaultValue={query.sort} aria-label={t.stockList.sortBy} className={field}>
          {STOCK_SORTS.map((sort) => (
            <option key={sort} value={sort}>
              {t.stockList.sortBy}: {t.stockList.sorts[sort]}
            </option>
          ))}
        </select>
        {query.change && <input type="hidden" name="change" value={query.change} />}
        <select name="order" defaultValue={query.order} aria-label={t.stockList.sortBy} className={field}>
          <option value="desc">{t.stockList.order.desc}</option>
          <option value="asc">{t.stockList.order.asc}</option>
        </select>
        <button type="submit" className="rounded-md bg-chip px-4 py-1.5 text-sm font-semibold hover:bg-line">
          {t.stockList.searchButton}
        </button>
      </form>

      <p className="text-sm text-muted">{t.stockList.found(list.total)}</p>

      {list.stocks.length === 0 ? (
        <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.stockList.noMatch}</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-line bg-card">
          <table className="w-full min-w-[44rem] text-sm tabular-nums">
            <thead className="bg-card-strong text-left text-xs text-faint">
              <tr>
                <th className="px-3 py-2 font-medium">{t.stockList.stock}</th>
                <th className="px-3 py-2 font-medium">{t.stockList.sector}</th>
                <th className="px-3 py-2 text-right font-medium">{t.stockList.last}</th>
                <th className="px-3 py-2 text-right font-medium">{t.stockList.change}</th>
                <th className="px-3 py-2 text-right font-medium">{t.stockList.value}</th>
                <th className="px-3 py-2 text-right font-medium">{t.stockList.marketCap}</th>
              </tr>
            </thead>
            <tbody>
              {list.stocks.map((s) => (
                <tr key={s.ticker} className="border-t border-line">
                  <td className="max-w-64 px-3 py-2">
                    <Link href={`/${locale}/stocks/${s.ticker}`} className="group block">
                      <span className="font-mono font-semibold group-hover:text-accent group-hover:underline">
                        {s.ticker}
                      </span>
                      {s.brokerTracked && (
                        <span
                          title={t.stockList.brokerTagTitle}
                          className="ml-2 rounded bg-chip px-1.5 py-0.5 text-[10px] font-semibold text-muted"
                        >
                          {t.stockList.brokerTag}
                        </span>
                      )}
                      <span className="block truncate text-xs text-faint">{s.name}</span>
                    </Link>
                  </td>
                  <td className="px-3 py-2 text-xs text-muted">{s.sector ?? t.stockList.unknownSector}</td>
                  <td className="px-3 py-2 text-right">{f.price(s.lastPrice)}</td>
                  <td className={`px-3 py-2 text-right ${changeColor(s.changePercent)}`}>{f.percent(s.changePercent)}</td>
                  <td className="px-3 py-2 text-right">{f.rupiah(s.value)}</td>
                  <td className="px-3 py-2 text-right">{f.rupiah(s.marketCap)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pages > 1 && (
        <nav className="flex items-center justify-between text-sm" aria-label={t.stockList.pageOf(list.page, pages)}>
          {list.page > 1 ? (
            <Link href={pageHref(list.page - 1)} className="text-accent hover:underline">
              {t.stockList.previous}
            </Link>
          ) : (
            <span />
          )}
          <span className="text-muted">{t.stockList.pageOf(list.page, pages)}</span>
          {list.page < pages ? (
            <Link href={pageHref(list.page + 1)} className="text-accent hover:underline">
              {t.stockList.next}
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}

      <p className="text-xs text-faint">
        {t.common.source}: {list.dataSource}
        {list.dataDate && <> · {t.common.endOfDayFor(f.tradingDate(list.dataDate))}</>}
        {" · "}
        {list.demo ? t.common.demoFigures : t.common.estValueNote}
      </p>
    </div>
  );
}
