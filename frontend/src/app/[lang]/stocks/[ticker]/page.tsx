import Link from "next/link";
import { Suspense } from "react";
import { StockOverview } from "@/components/stock-overview";
import { parseInterval, type TrackOutcome } from "@/lib/api";
import { getI18n } from "@/lib/i18n";

export default async function StockPage({ params, searchParams }: PageProps<"/[lang]/stocks/[ticker]">) {
  const { locale, t } = await getI18n();
  // ticker and searchParams are request-time data, so they are read inside <Suspense>
  // (required with cacheComponents). The back link and layout render immediately.
  const content = Promise.all([params, searchParams]).then(([{ ticker }, query]) => (
    <StockOverview
      ticker={ticker.toUpperCase()}
      interval={parseInterval(query.tf)}
      range={typeof query.range === "string" ? query.range.toUpperCase() : undefined}
      tracking={parseTracking(query.tracking)}
    />
  ));

  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <Link href={`/${locale}`} className="text-sm text-muted hover:text-fg">
          {t.stock.back}
        </Link>
        <div className="mt-4">
          <Suspense fallback={<p className="text-muted">{t.common.loadingStock}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

const TRACK_OUTCOMES: TrackOutcome[] = [
  "TRACKED", "ALREADY_TRACKED", "UNKNOWN_STOCK", "TOTAL_LIMIT_REACHED", "DAILY_LIMIT_REACHED", "FAILED",
];

function parseTracking(value: string | string[] | undefined): TrackOutcome | undefined {
  return TRACK_OUTCOMES.find((o) => o === value);
}
