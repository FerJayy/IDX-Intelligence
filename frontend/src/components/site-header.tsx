import Link from "next/link";
import { getI18n } from "@/lib/i18n";
import { LanguageSwitch } from "./language-switch";
import { ThemeSwitch } from "./theme-switch";

export async function SiteHeader() {
  const { locale, t } = await getI18n();
  return (
    <header className="border-b border-line bg-card">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-8">
        <div className="flex items-center gap-6">
          <Link href={`/${locale}`} className="font-bold tracking-tight">
            {t.common.appName}
          </Link>
          <nav className="flex gap-4 text-sm text-muted">
            <Link href={`/${locale}`} className="hover:text-fg">
              {t.nav.overview}
            </Link>
            <Link href={`/${locale}/stocks`} className="hover:text-fg">
              {t.nav.stocks}
            </Link>
            <Link href={`/${locale}/radar`} className="hover:text-fg">
              {t.nav.radar}
            </Link>
            <Link href={`/${locale}/brokers`} className="hover:text-fg">
              {t.nav.brokers}
            </Link>
            <Link href={`/${locale}/ihsg`} className="hover:text-fg">
              {t.nav.ihsg}
            </Link>
          </nav>
        </div>
        <div className="flex items-center gap-2">
          <LanguageSwitch locale={locale} labels={t.language} />
          <ThemeSwitch labels={t.theme} />
        </div>
      </div>
    </header>
  );
}
