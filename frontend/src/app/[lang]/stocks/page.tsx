import { Suspense } from "react";
import { StockTable } from "@/components/stock-table";
import { STOCK_SORTS, type StockListQuery, type StockSort } from "@/lib/api";
import { getI18n } from "@/lib/i18n";

export default async function StocksPage({ searchParams }: PageProps<"/[lang]/stocks">) {
  const { t } = await getI18n();
  // searchParams is request-time data, so it's read inside <Suspense> (required with cacheComponents).
  const content = searchParams.then((query) => <StockTable query={parseQuery(query)} />);

  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <h1 className="text-2xl font-bold">{t.stockList.title}</h1>
        <div className="mt-6">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

function parseQuery(query: Record<string, string | string[] | undefined>): StockListQuery {
  const one = (key: string) => (typeof query[key] === "string" ? (query[key] as string) : undefined);
  const sort = one("sort");
  const page = Number(one("page"));
  return {
    search: one("search")?.slice(0, 50),
    sector: one("sector"),
    change: ["up", "down", "flat"].includes(one("change") ?? "") ? (one("change") as "up" | "down" | "flat") : undefined,
    sort: (STOCK_SORTS as readonly string[]).includes(sort ?? "") ? (sort as StockSort) : "marketCap",
    order: one("order") === "asc" ? "asc" : "desc",
    page: Number.isInteger(page) && page > 0 ? page : 1,
  };
}
