import { toggleWatch } from "@/lib/actions";

/** Star toggle; a plain form, so it works without client JavaScript. */
export function WatchButton({
  ticker,
  watched,
  labels,
}: {
  ticker: string;
  watched: boolean;
  labels: { add: string; remove: string };
}) {
  return (
    <form action={toggleWatch}>
      <input type="hidden" name="ticker" value={ticker} />
      <button
        type="submit"
        aria-pressed={watched}
        className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm ${
          watched ? "border-warn-line bg-warn-bg font-semibold text-warn-fg" : "border-line text-muted hover:bg-chip hover:text-fg"
        }`}
      >
        <span aria-hidden>{watched ? "★" : "☆"}</span>
        {watched ? labels.remove : labels.add}
      </button>
    </form>
  );
}
