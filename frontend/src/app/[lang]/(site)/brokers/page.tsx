import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { getBrokers, type BrokerListRow } from "@/lib/api";
import { changeColor } from "@/lib/format";
import { getI18n } from "@/lib/i18n";

export default async function BrokersPage() {
  const { t } = await getI18n();
  return (
    <main className="flex-1 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-4xl">
        <h1 className="text-2xl font-bold">{t.brokers.title}</h1>
        <p className="mt-2 text-sm text-muted">{t.brokers.intro}</p>
        <div className="mt-6">
          <Suspense fallback={<p className="text-muted">{t.common.loadingMarket}</p>}>
            <BrokerList />
          </Suspense>
        </div>
      </div>
    </main>
  );
}

async function BrokerList() {
  const { locale, t, f } = await getI18n();
  let brokers: BrokerListRow[];
  try {
    brokers = await getBrokers();
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    return <div className="rounded-xl border border-danger-line bg-danger-bg p-6 text-danger-fg">{t.common.backendDown}</div>;
  }
  if (brokers.length === 0) {
    return <p className="rounded-xl border border-line bg-card p-6 text-sm text-faint">{t.brokers.empty}</p>;
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-card">
      <table className="w-full text-sm tabular-nums">
        <thead className="bg-card-strong text-left text-xs text-faint">
          <tr>
            <th className="px-3 py-2 font-medium">{t.brokers.code}</th>
            <th className="hidden px-3 py-2 text-right font-medium sm:table-cell">{t.brokers.stocks}</th>
            <th className="hidden px-3 py-2 text-right font-medium sm:table-cell">{t.brokers.gross}</th>
            <th className="px-3 py-2 text-right font-medium">{t.brokers.net}</th>
          </tr>
        </thead>
        <tbody>
          {brokers.map((b) => (
            <tr key={b.code} className="border-t border-line hover:bg-card-strong">
              <td className="w-full max-w-0 px-3 py-2">
                <Link href={`/${locale}/brokers/${b.code}`} className="group block">
                  <span className="font-mono font-semibold group-hover:text-accent group-hover:underline">{b.code}</span>
                  <span className="block truncate text-xs text-faint sm:ml-2 sm:inline">{b.name}</span>
                </Link>
              </td>
              <td className="hidden px-3 py-2 text-right sm:table-cell">{b.stocks}</td>
              <td className="hidden px-3 py-2 text-right sm:table-cell">{f.rupiah(b.grossValue)}</td>
              <td className={`px-3 py-2 text-right ${changeColor(b.netValue)}`}>{f.signedRupiah(b.netValue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
