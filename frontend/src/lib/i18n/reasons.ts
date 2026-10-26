import type { Format } from "@/lib/format";
import type { Dictionary } from "./en";

/** A language-neutral explanation from the backend (Reason.java). */
export type Reason = {
  code: string;
  values: Record<string, unknown>;
  points: number | null;
};

/** Turns a reason code into a sentence; unknown codes (newer backend) fall back to the code itself. */
export function describe(reason: Reason, t: Dictionary, f: Format): string {
  const template = (t.reasons as Record<string, ((v: Record<string, unknown>, f: Format) => string) | undefined>)[reason.code];
  return template ? template(reason.values ?? {}, f) : reason.code;
}
