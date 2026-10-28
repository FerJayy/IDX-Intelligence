import { backendFetch } from "@/lib/api";

// Called by the log-in and register pages as soon as they open: if the backend is asleep (free hosting
// sleeps after ~20 idle minutes) it starts waking now, while the visitor is still typing.
export const maxDuration = 60;

export async function GET() {
  try {
    await backendFetch("/api/health", { signal: AbortSignal.timeout(55_000) });
  } catch {
    // Waking is best effort; the form still works, just slower.
  }
  return new Response(null, { status: 204 });
}
