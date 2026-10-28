import { Suspense } from "react";
import { AutoRefresh } from "@/components/auto-refresh";
import { MarketOverview, MOVER_TABS, type MoverTab } from "@/components/market-overview";
import { getI18n } from "@/lib/i18n";

export default async function Home({ searchParams }: PageProps<"/[lang]">) {
  const { t } = await getI18n();
  // searchParams (the movers tab) is request-time data, so it's read inside <Suspense>.
  const content = searchParams.then((query) => <MarketOverview tab={parseTab(query.movers)} />);
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <p className="text-muted">{t.common.tagline}</p>
        {/* Today's live lists and breadth refresh themselves during the session. */}
        <AutoRefresh />
        <div className="mt-6">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

function parseTab(value: string | string[] | undefined): MoverTab {
  return typeof value === "string" && (MOVER_TABS as readonly string[]).includes(value) ? (value as MoverTab) : "gainers";
}
