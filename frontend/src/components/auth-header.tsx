import { getI18n } from "@/lib/i18n";
import { LanguageSwitch } from "./language-switch";
import { ThemeSwitch } from "./theme-switch";

/** Header for the account pages: the name only, no menu (the rest of the site needs a login). */
export async function AuthHeader() {
  const { locale, t } = await getI18n();
  return (
    <header className="border-b border-line bg-card">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 sm:px-8">
        <span className="font-bold tracking-tight">{t.common.appName}</span>
        <div className="flex items-center gap-2">
          <LanguageSwitch locale={locale} labels={t.language} />
          <ThemeSwitch labels={t.theme} />
        </div>
      </div>
    </header>
  );
}
