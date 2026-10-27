import { getLivePrice } from "@/lib/api";

// Lets the browser poll the live price without knowing the backend's address (API_BASE_URL is
// server-only). The backend caches per stock for 60 s and enforces the daily live budget.
export async function GET(_request: Request, ctx: RouteContext<"/api/live/[ticker]">) {
  const { ticker } = await ctx.params;
  if (!/^[A-Za-z]{4}$/.test(ticker)) {
    return Response.json({ error: "invalid ticker" }, { status: 400 });
  }
  try {
    return Response.json(await getLivePrice(ticker.toUpperCase()));
  } catch (error) {
    console.error(error);
    return Response.json({ status: "UNAVAILABLE", quote: null, fetchedAt: null, budgetLeft: 0 });
  }
}
