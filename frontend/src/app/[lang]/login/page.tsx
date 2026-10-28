import Link from "next/link";
import { Suspense } from "react";
import { AuthCard, Field, Notice, SubmitButton } from "@/components/auth-form";
import { WakeBackend } from "@/components/wake-backend";
import { login } from "@/lib/actions";
import { getI18n } from "@/lib/i18n";

// The backend can take up to a minute to wake up; let this page's Server Actions wait for it.
export const maxDuration = 60;

export default async function LoginPage({ searchParams }: PageProps<"/[lang]/login">) {
  const { locale, t } = await getI18n();
  const a = t.auth;
  // The message and the page to return to come from the URL: request-time data, inside <Suspense>.
  const form = searchParams.then((query) => {
    const error = typeof query.error === "string" ? query.error : null;
    const next = typeof query.next === "string" ? query.next : `/${locale}`;
    return (
      <>
        {query.registered === "1" && <Notice kind="info">{a.registered}</Notice>}
        {query.reset === "1" && <Notice kind="info">{a.resetDone}</Notice>}
        {error && <Notice kind="error">{a.errors[error] ?? a.genericError}</Notice>}
        <form action={login} className="space-y-4">
          <input type="hidden" name="lang" value={locale} />
          <input type="hidden" name="next" value={next} />
          <Field label={a.email} name="email" type="email" autoComplete="email" />
          <Field label={a.password} name="password" type="password" autoComplete="current-password" />
          <SubmitButton>{a.loginButton}</SubmitButton>
        </form>
      </>
    );
  });
  return (
    <AuthCard title={a.loginTitle} intro={a.loginIntro}>
      <WakeBackend />
      <Suspense fallback={null}>{form}</Suspense>
      <div className="mt-5 space-y-1 text-sm">
        <p>
          <Link href={`/${locale}/forgot-password`} className="text-accent hover:underline">
            {a.forgotLink}
          </Link>
        </p>
        <p className="text-muted">
          {a.noAccount}{" "}
          <Link href={`/${locale}/register`} className="font-semibold text-accent hover:underline">
            {a.registerLink}
          </Link>
        </p>
      </div>
    </AuthCard>
  );
}
