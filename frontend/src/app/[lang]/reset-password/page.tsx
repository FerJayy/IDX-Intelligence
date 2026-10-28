import Link from "next/link";
import { Suspense } from "react";
import { AuthCard, Field, Notice, SubmitButton } from "@/components/auth-form";
import { chooseNewPassword } from "@/lib/actions";
import { getI18n } from "@/lib/i18n";

// The backend can take up to a minute to wake up; let this page's Server Actions wait for it.
export const maxDuration = 60;

// The reset token is in this page's URL: never send it to other sites as a Referer.
export const metadata = { referrer: "no-referrer" };

export default async function ResetPasswordPage({ searchParams }: PageProps<"/[lang]/reset-password">) {
  const { locale, t } = await getI18n();
  const a = t.auth;
  // The token from the emailed link is request-time data, so the form renders inside <Suspense>.
  const form = searchParams.then((query) => {
    const token = typeof query.token === "string" ? query.token : "";
    const error = typeof query.error === "string" ? query.error : !token ? "INVALID_LINK" : null;
    return (
      <>
        {error && <Notice kind="error">{a.errors[error] ?? a.genericError}</Notice>}
        {token && (
          <form action={chooseNewPassword} className="space-y-4">
            <input type="hidden" name="lang" value={locale} />
            <input type="hidden" name="token" value={token} />
            <Field label={a.newPassword} name="password" type="password" autoComplete="new-password" hint={a.passwordHint} />
            <Field label={a.confirm} name="confirm" type="password" autoComplete="new-password" />
            <SubmitButton>{a.resetButton}</SubmitButton>
          </form>
        )}
      </>
    );
  });
  return (
    <AuthCard title={a.resetTitle} intro={a.resetIntro}>
      <Suspense fallback={null}>{form}</Suspense>
      <p className="mt-5 text-sm">
        <Link href={`/${locale}/forgot-password`} className="text-accent hover:underline">
          {a.forgotLink}
        </Link>
        {" · "}
        <Link href={`/${locale}/login`} className="text-accent hover:underline">
          {a.loginLink}
        </Link>
      </p>
    </AuthCard>
  );
}
