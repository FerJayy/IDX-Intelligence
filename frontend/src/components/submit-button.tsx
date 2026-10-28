"use client";

import type { ReactNode } from "react";
import { useFormStatus } from "react-dom";

/**
 * Disabled while its form is being sent, so a second click can't submit again (the first request can
 * take a while when the backend is waking up).
 */
export function SubmitButton({ children }: { children: ReactNode }) {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      disabled={pending}
      aria-busy={pending}
      className="w-full rounded-md bg-fg px-4 py-2 text-sm font-semibold text-page hover:opacity-90 disabled:cursor-wait disabled:opacity-60"
    >
      {pending ? "…" : children}
    </button>
  );
}
