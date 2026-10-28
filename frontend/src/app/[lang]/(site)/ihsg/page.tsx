import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { ChartControls } from "@/components/chart-controls";
import { DemoBanner } from "@/components/demo-banner";
import { IhsgLive } from "@/components/ihsg-live";
import { PriceChart } from "@/components/price-chart";
import { getIndex, getIndexCandles, parseInterval, type CandleSeries, type IndexDetail, type Interval } from "@/lib/api";
import { getI18n } from "@/lib/i18n";

export default async function IhsgPage({ searchParams }: PageProps<"/[lang]/ihsg">) {
  const { locale, t } = await getI18n();
  const content = searchParams.then((query) => (
    <IhsgChart
      interval={parseInterval(query.tf)}
      range={typeof query.range === "string" ? query.range.toUpperCase() : undefined}
    />
  ));
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-6xl">
        <Link href={`/${locale}`} className="text-sm text-muted hover:text-fg">
          {t.ihsg.back}
        </Link>
        <div className="mt-4">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>{content}</Suspense>
        </div>
      </div>
    </main>
  );
}

async function IhsgChart({ interval, range }: { interval: Interval; range?: string }) {
  const { locale, t, f } = await getI18n();
  let detail: IndexDetail;
  let chart: CandleSeries | null;
  try {
    [detail, chart] = await Promise.all([getIndex(), getIndexCandles(interval, range)]);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.ihsg.cannotLoad}</div>;
  }
  const index = detail.index;

  return (
    <div className="space-y-6">
      {detail.demo && <DemoBanner notice={t.common.demoNotice} />}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-2xl font-bold">{t.ihsg.title}</h1>
        {index && (
          <div>
            <IhsgLive
              align="right"
              locale={locale}
              close={index.value}
              change={index.change}
              changePercent={index.changePercent}
              closeDate={index.tradeDate}
              labels={{ live: t.live.badge, delayed: t.dashboard.ihsgDelayed, closed: t.live.closedToday }}
            />
            {index.tradeDate && <p className="text-right text-xs text-faint">{t.stock.closeOf(f.tradingDate(index.tradeDate))}</p>}
          </div>
        )}
      </header>

      {chart && (
        <section className="space-y-4 rounded-xl border border-line bg-card p-4">
          <ChartControls
            interval={interval}
            range={chart.range}
            ranges={chart.ranges}
            hrefFor={(i, r) => `/${locale}/ihsg?tf=${i}${r ? `&range=${r}` : ""}`}
            labels={t.chartControls}
          />
          <PriceChart
            locale={locale}
            labels={t.chart}
            candles={chart.candles}
            intraday={chart.intraday}
            live={{ symbol: "COMPOSITE", interval: chart.interval, range: chart.range, stock: false }}
          />
          {chart.intraday && <p className="text-xs text-faint">{t.chartControls.intradayNote}</p>}
          <p className="text-xs text-faint">
            {t.common.source}: {chart.dataSource} · {t.common.generated} {f.jakartaTime(chart.generatedAt)}
          </p>
        </section>
      )}
    </div>
  );
}
