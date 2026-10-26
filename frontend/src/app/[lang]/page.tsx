import { Suspense } from "react";
import { MarketOverview } from "@/components/market-overview";
import { getI18n } from "@/lib/i18n";

export default async function Home() {
  const { t } = await getI18n();
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <p className="text-muted">{t.common.tagline}</p>
        <div className="mt-6">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>
            <MarketOverview />
          </Suspense>
        </div>
      </div>
    </main>
  );
}
