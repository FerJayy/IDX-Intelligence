import Link from "next/link";
import { Suspense } from "react";
import { AuthCard, Field, Notice, SubmitButton } from "@/components/auth-form";
import { forgotPassword } from "@/lib/actions";
import { getI18n } from "@/lib/i18n";

export default async function ForgotPasswordPage({ searchParams }: PageProps<"/[lang]/forgot-password">) {
  const { locale, t } = await getI18n();
  const a = t.auth;
  const message = searchParams.then((query) =>
    query.sent === "1" ? (
      <Notice kind="info">{a.forgotSent}</Notice>
    ) : typeof query.error === "string" ? (
      <Notice kind="error">{a.errors[query.error] ?? a.genericError}</Notice>
    ) : null,
  );
  return (
    <AuthCard title={a.forgotTitle} intro={a.forgotIntro}>
      <Suspense fallback={null}>{message}</Suspense>
      <form action={forgotPassword} className="space-y-4">
        <input type="hidden" name="lang" value={locale} />
        <Field label={a.email} name="email" type="email" autoComplete="email" />
        <SubmitButton>{a.forgotButton}</SubmitButton>
      </form>
      <p className="mt-5 text-sm">
        <Link href={`/${locale}/login`} className="text-accent hover:underline">
          {a.backToLogin}
        </Link>
      </p>
    </AuthCard>
  );
}
