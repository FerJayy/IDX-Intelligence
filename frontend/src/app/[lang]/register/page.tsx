import Link from "next/link";
import { Suspense } from "react";
import { AuthCard, Field, Notice, SubmitButton } from "@/components/auth-form";
import { register } from "@/lib/actions";
import { getI18n } from "@/lib/i18n";

export default async function RegisterPage({ searchParams }: PageProps<"/[lang]/register">) {
  const { locale, t } = await getI18n();
  const a = t.auth;
  const message = searchParams.then((query) =>
    typeof query.error === "string" ? <Notice kind="error">{a.errors[query.error] ?? a.genericError}</Notice> : null,
  );
  return (
    <AuthCard title={a.registerTitle} intro={a.registerIntro}>
      <Suspense fallback={null}>{message}</Suspense>
      <form action={register} className="space-y-4">
        <input type="hidden" name="lang" value={locale} />
        <Field label={a.email} name="email" type="email" autoComplete="email" />
        <Field label={a.password} name="password" type="password" autoComplete="new-password" hint={a.passwordHint} />
        <Field label={a.confirm} name="confirm" type="password" autoComplete="new-password" />
        <SubmitButton>{a.registerButton}</SubmitButton>
      </form>
      <p className="mt-5 text-sm text-muted">
        {a.haveAccount}{" "}
        <Link href={`/${locale}/login`} className="font-semibold text-accent hover:underline">
          {a.loginLink}
        </Link>
      </p>
    </AuthCard>
  );
}
