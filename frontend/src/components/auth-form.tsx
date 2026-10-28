import type { ReactNode } from "react";

/** Centered card used by the log-in, register and password pages. */
export function AuthCard({ title, intro, children }: { title: string; intro?: string; children: ReactNode }) {
  return (
    <main className="flex flex-1 items-start justify-center px-4 py-12">
      <div className="w-full max-w-sm rounded-xl border border-line bg-card p-6">
        <h1 className="text-xl font-bold">{title}</h1>
        {intro && <p className="mt-2 text-sm text-muted">{intro}</p>}
        <div className="mt-5">{children}</div>
      </div>
    </main>
  );
}

export function Field({
  label,
  name,
  type,
  autoComplete,
  hint,
}: {
  label: string;
  name: string;
  type: "email" | "password";
  autoComplete: string;
  hint?: string;
}) {
  return (
    <label className="block text-sm">
      <span className="text-muted">{label}</span>
      <input
        name={name}
        type={type}
        required
        autoComplete={autoComplete}
        minLength={type === "password" && autoComplete === "new-password" ? 8 : undefined}
        maxLength={type === "password" ? 72 : 254}
        className="mt-1 w-full rounded-md border border-line bg-page px-3 py-2 text-fg"
      />
      {hint && <span className="mt-1 block text-xs text-faint">{hint}</span>}
    </label>
  );
}

export { SubmitButton } from "./submit-button";

export function Notice({ kind, children }: { kind: "error" | "info"; children: ReactNode }) {
  const style = kind === "error" ? "border-danger-line bg-danger-bg text-danger-fg" : "border-info-line bg-info-bg text-info-fg";
  return <p className={`mb-4 rounded-lg border px-3 py-2 text-sm ${style}`}>{children}</p>;
}
