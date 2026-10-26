export function DemoBanner({ notice }: { notice: string | null }) {
  return (
    <div className="rounded-lg border border-amber-500/60 bg-amber-500/10 px-4 py-3 text-center text-sm font-semibold tracking-wide text-amber-300">
      {notice ?? "DEMO DATA — NOT LIVE MARKET DATA"}
    </div>
  );
}
