import Link from "next/link";
import { INTERVALS, type Interval } from "@/lib/api";

/** Interval (candle size) and range buttons. Plain links, so each view has its own URL. */
export function ChartControls({
  interval,
  range,
  ranges,
  hrefFor,
  labels,
}: {
  interval: Interval;
  range: string;
  ranges: string[];
  hrefFor: (interval: Interval, range?: string) => string;
  labels: { interval: string; range: string; intervals: Record<Interval, string> };
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
      <nav className="flex gap-1" aria-label={labels.interval}>
        {INTERVALS.map((i) => (
          <Button key={i} href={hrefFor(i)} active={i === interval}>
            {labels.intervals[i]}
          </Button>
        ))}
      </nav>
      <nav className="flex gap-1" aria-label={labels.range}>
        {ranges.map((r) => (
          <Button key={r} href={hrefFor(interval, r)} active={r === range}>
            {r}
          </Button>
        ))}
      </nav>
    </div>
  );
}

function Button({ href, active, children }: { href: string; active: boolean; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      scroll={false}
      aria-current={active ? "page" : undefined}
      className={`rounded-md px-2.5 py-1 text-sm ${
        active ? "bg-chip font-semibold text-fg" : "text-muted hover:bg-chip hover:text-fg"
      }`}
    >
      {children}
    </Link>
  );
}
