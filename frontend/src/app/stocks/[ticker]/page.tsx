import Link from "next/link";
import { Suspense } from "react";
import { StockOverview } from "@/components/stock-overview";
import { PRICE_RANGES, type PriceRange } from "@/lib/api";

export default function StockPage({ params, searchParams }: PageProps<"/stocks/[ticker]">) {
  // params and searchParams are request-time data, so they are read inside <Suspense>
  // (required with cacheComponents). The back link and layout render immediately.
  const content = Promise.all([params, searchParams]).then(([{ ticker }, query]) => (
    <StockOverview ticker={ticker.toUpperCase()} range={parseRange(query.range)} />
  ));

  return (
    <main className="min-h-screen bg-slate-950 px-4 py-8 text-white sm:px-8">
      <div className="mx-auto max-w-6xl">
        <Link href="/" className="text-sm text-slate-400 hover:text-slate-200">
          ← Market overview
        </Link>
        <div className="mt-4">
          <Suspense fallback={<p className="text-slate-400">Loading stock data…</p>}>
            {content}
          </Suspense>
        </div>
      </div>
    </main>
  );
}

function parseRange(value: string | string[] | undefined): PriceRange {
  const upper = typeof value === "string" ? value.toUpperCase() : "";
  return (PRICE_RANGES as readonly string[]).includes(upper) ? (upper as PriceRange) : "3M";
}
