import { getIndexCandles, getStockCandles, INTERVALS, type Interval } from "@/lib/api";

// Lets an open chart refresh its intraday candles without knowing the backend's address
// (API_BASE_URL is server-only). The backend caches intraday candles for 2 minutes per symbol.
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const symbol = (params.get("symbol") ?? "").toUpperCase();
  const interval = params.get("interval") ?? "";
  const range = params.get("range") ?? undefined;
  if (
    !(symbol === "COMPOSITE" || /^[A-Z]{4}$/.test(symbol)) ||
    !(INTERVALS as readonly string[]).includes(interval) ||
    (range != null && !/^[0-9A-Z]{1,4}$/.test(range))
  ) {
    return Response.json({ error: "invalid request" }, { status: 400 });
  }
  try {
    const series =
      symbol === "COMPOSITE"
        ? await getIndexCandles(interval as Interval, range)
        : await getStockCandles(symbol, interval as Interval, range);
    return series ? Response.json(series) : Response.json({ error: "not found" }, { status: 404 });
  } catch (error) {
    console.error(error);
    return Response.json({ error: "unavailable" }, { status: 502 });
  }
}
