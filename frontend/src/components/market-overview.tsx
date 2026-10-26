import { unstable_rethrow } from "next/navigation";
import { getMarketOverview, type MarketOverview as Overview } from "@/lib/api";
import { changeColor } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import { DemoBanner } from "./demo-banner";
import { StockList } from "./stock-list";

// Server Component: fetches on the server at request time, streamed in via <Suspense>.
export async function MarketOverview() {
  const { locale, t, f } = await getI18n();
  let overview: Overview;
  try {
    overview = await getMarketOverview();
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return (
      <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">
        {t.common.backendDown}
      </div>
    );
  }

  const index = overview.compositeIndex;

  return (
    <div className="space-y-6">
      {overview.demo && <DemoBanner notice={t.common.demoNotice} />}

      <div className="grid gap-4 sm:grid-cols-2">
        <section className="rounded-xl border border-line bg-card p-5">
          <p className="text-sm text-muted">{t.overview.ihsg}</p>
          {index ? (
            <>
              <p className="mt-2 text-3xl font-bold tabular-nums">{f.index(index.value)}</p>
              <p className={`mt-1 tabular-nums ${changeColor(index.change)}`}>
                {f.change(index.change, 2)} ({f.percent(index.changePercent)})
              </p>
            </>
          ) : (
            <p className="mt-2 text-sm text-faint">{t.overview.ihsgMissing}</p>
          )}
        </section>

        <section className="rounded-xl border border-line bg-card p-5">
          <p className="text-sm text-muted">{t.overview.breadth}</p>
          <div className="mt-2 grid grid-cols-3 text-center">
            <Breadth label={t.overview.up} count={overview.advancers} color="text-up" />
            <Breadth label={t.overview.down} count={overview.decliners} color="text-down" />
            <Breadth label={t.overview.flat} count={overview.unchanged} color="text-fg" />
          </div>
        </section>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <StockList locale={locale} title={t.overview.topGainers} stocks={overview.topGainers} metric="changePercent" />
        <StockList locale={locale} title={t.overview.topLosers} stocks={overview.topLosers} metric="changePercent" />
        <StockList locale={locale} title={t.overview.mostActive} stocks={overview.mostActive} metric="value" />
      </div>

      <p className="text-xs text-faint">
        {t.common.source}: {overview.dataSource}
        {overview.dataDate && <> · {t.common.endOfDayFor(f.tradingDate(overview.dataDate))}</>}
        {" · "}
        {t.common.generated} {f.jakartaTime(overview.generatedAt)}
        {" · "}
        {overview.demo ? t.common.demoFigures : t.common.estValueNote}
      </p>
    </div>
  );
}

function Breadth({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <div>
      <p className={`text-3xl font-bold tabular-nums ${color}`}>{count}</p>
      <p className="text-xs text-faint">{label}</p>
    </div>
  );
}
