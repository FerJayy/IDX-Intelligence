"use client";

import { useEffect } from "react";

/** Starts waking the backend when an account page opens (see app/api/wake/route.ts). */
export function WakeBackend() {
  useEffect(() => {
    fetch("/api/wake", { cache: "no-store" }).catch(() => {});
  }, []);
  return null;
}
