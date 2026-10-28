import { cookies } from "next/headers";
import { USER_COOKIE } from "@/lib/api";
import { logout } from "@/lib/actions";
import { getI18n } from "@/lib/i18n";

/** "Logged in as … · Log out" in the header. Reads a cookie, so the header renders it inside <Suspense>. */
export async function UserMenu() {
  const { locale, t } = await getI18n();
  const email = (await cookies()).get(USER_COOKIE)?.value;
  if (!email) return null;
  return (
    <form action={logout} className="flex items-center gap-2 text-xs">
      <input type="hidden" name="lang" value={locale} />
      <span className="hidden max-w-48 truncate text-faint sm:inline" title={t.auth.loggedInAs(email)}>
        {email}
      </span>
      <button type="submit" className="rounded-md border border-line px-2 py-1 text-muted hover:bg-chip hover:text-fg">
        {t.auth.logout}
      </button>
    </form>
  );
}
