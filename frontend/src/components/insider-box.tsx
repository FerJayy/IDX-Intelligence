import { unstable_rethrow } from "next/navigation";
import { getInsiders, type InsiderTransaction, type Insiders } from "@/lib/api";
import { changeColor, type Format } from "@/lib/format";
import { getI18n } from "@/lib/i18n";
import type { Dictionary } from "@/lib/i18n/en";

/** Latest insider disclosures for one stock; sits in its own <Suspense> so the page doesn't wait for it. */
export async function InsiderBox({ ticker }: { ticker: string }) {
  const { t, f } = await getI18n();
  let data: Insiders | null;
  try {
    data = await getInsiders(ticker);
  } catch (error) {
    unstable_rethrow(error);
    console.error(error);
    data = null;
  }

  return (
    <section className="rounded-xl border border-line bg-card p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t.insiders.title}</h2>
      <p className="mt-1 text-xs text-faint">{t.insiders.intro}</p>
      {!data ? (
        <p className="mt-3 text-sm text-faint">{t.insiders.unavailable}</p>
      ) : data.transactions.length === 0 ? (
        <p className="mt-3 text-sm text-faint">{data.fetchedAt ? t.insiders.none : t.insiders.notLoaded}</p>
      ) : (
        <ul className="mt-3 divide-y divide-line text-sm">
          {data.transactions.map((tx, i) => (
            <li key={`${tx.date}-${tx.name}-${i}`} className="py-2 leading-relaxed">
              <span className="text-xs text-faint">{f.tradingDate(tx.date)}</span>
              <p>
                <span className="font-medium">{tx.name}</span>
                {tx.nationality === "foreign" && <span className="ml-1 text-xs text-faint">({t.insiders.foreign})</span>}{" "}
                <Action tx={tx} t={t} f={f} />
                {tx.price != null && t.insiders.atPrice(f.price(tx.price))}
                {tx.percentAfter != null && <span className="text-muted"> · {t.insiders.nowHolds(f.holding(tx.percentAfter))}</span>}
              </p>
            </li>
          ))}
        </ul>
      )}
      {data && (
        <p className="mt-2 text-xs text-faint">
          {data.total != null && data.total > data.transactions.length && <>{t.insiders.more(data.transactions.length, data.total)} </>}
          {data.source}
          {data.fetchedAt && <> · {t.insiders.checked(f.jakartaTime(data.fetchedAt))}</>}
        </p>
      )}
    </section>
  );
}

function Action({ tx, t, f }: { tx: InsiderTransaction; t: Dictionary; f: Format }) {
  const shares = f.price(tx.shareChange == null ? null : Math.abs(tx.shareChange));
  // Colour follows the direction of the holding change, with the word always present.
  const className = `font-semibold ${changeColor(tx.shareChange)}`;
  if (tx.action === "buy") return <span className={className}>{t.insiders.bought(shares)}</span>;
  if (tx.action === "sell") return <span className={className}>{t.insiders.sold(shares)}</span>;
  if (tx.action === "cross") return <span className={className}>{t.insiders.cross(shares)}</span>;
  return <span className={className}>{t.insiders.other(tx.action, shares)}</span>;
}
