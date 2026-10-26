import { Suspense } from "react";
import { MarketOverview } from "@/components/market-overview";

export default function Home() {
  return (
    <main className="min-h-screen bg-slate-950 px-4 py-8 text-white sm:px-8">
      <div className="mx-auto max-w-6xl">
        <header>
          <h1 className="text-3xl font-bold sm:text-4xl">IDX Intelligence</h1>
          <p className="mt-2 text-slate-400">
            Indonesian stock market overview. Research tool, not investment advice.
          </p>
        </header>

        <div className="mt-8">
          <Suspense fallback={<p className="text-slate-400">Loading market data…</p>}>
            <MarketOverview />
          </Suspense>
        </div>
      </div>
    </main>
  );
}
