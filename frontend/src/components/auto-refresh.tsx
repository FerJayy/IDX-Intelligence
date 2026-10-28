"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { sessionOpenNow } from "@/lib/session";

/**
 * Re-renders the page's server data every few minutes while the market trades and the tab is visible,
 * for at most an hour. The backend caches the live lists, so this never costs more provider requests.
 */
export function AutoRefresh({ everyMs = 5 * 60_000, forMs = 60 * 60_000 }: { everyMs?: number; forMs?: number }) {
  const router = useRouter();
  useEffect(() => {
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > forMs) {
        window.clearInterval(timer);
        return;
      }
      if (document.visibilityState === "visible" && sessionOpenNow()) router.refresh();
    }, everyMs);
    return () => window.clearInterval(timer);
  }, [router, everyMs, forMs]);
  return null;
}
