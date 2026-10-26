import { unstable_rethrow } from "next/navigation";
import { getMarketOverview, type MarketOverview as Overview } from "@/lib/api";
import {
  changeColor,
  formatChange,
  formatIndex,
  formatJakartaTime,
  formatPercent,
} from "@/lib/format";
import { DemoBanner } from "./demo-banner";
import { StockList } from "./stock-list";

// Server Component: fetches on the server at request time, streamed in via <Suspense>.
export async function MarketOverview() {
  let overview: Overview;
  try {
    overview = await getMarketOverview();
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-red-900 bg-red-950/40 p-6 text-red-300">
        Cannot load market data. Check that the backend is running on port 8080.
      </div>
    );
  }

  const index = overview.compositeIndex;

  return (
    <div className="space-y-6">
      {overview.demo && <DemoBanner notice={overview.notice} />}

      <div className="grid gap-4 sm:grid-cols-2">
        <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <p className="text-sm text-slate-400">{index.name}</p>
          <p className="mt-2 text-3xl font-bold tabular-nums">
            {formatIndex(index.value)}
          </p>
          <p className={`mt-1 tabular-nums ${changeColor(index.change)}`}>
            {formatChange(index.change, 2)} ({formatPercent(index.changePercent)})
          </p>
        </section>

        <section className="rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <p className="text-sm text-slate-400">Market breadth</p>
          <div className="mt-2 grid grid-cols-3 text-center">
            <Breadth label="Up" count={overview.advancers} color="text-emerald-400" />
            <Breadth label="Down" count={overview.decliners} color="text-red-400" />
            <Breadth label="Flat" count={overview.unchanged} color="text-slate-300" />
          </div>
        </section>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <StockList title="Top gainers" stocks={overview.topGainers} metric="changePercent" />
        <StockList title="Top losers" stocks={overview.topLosers} metric="changePercent" />
        <StockList title="Most active" stocks={overview.mostActive} metric="value" />
      </div>

      <p className="text-xs text-slate-500">
        Source: {overview.dataSource} · Generated {formatJakartaTime(overview.generatedAt)}
        {overview.demo && " · Figures are invented for development and are not market prices."}
      </p>
    </div>
  );
}

function Breadth({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <div>
      <p className={`text-3xl font-bold tabular-nums ${color}`}>{count}</p>
      <p className="text-xs text-slate-500">{label}</p>
    </div>
  );
}
