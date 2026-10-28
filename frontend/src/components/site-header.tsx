import Link from "next/link";
import { Suspense } from "react";
import { getI18n } from "@/lib/i18n";
import { LanguageSwitch } from "./language-switch";
import { NavLinks, NavList, type NavItem } from "./nav-links";
import { ThemeSwitch } from "./theme-switch";
import { UserMenu } from "./user-menu";

export async function SiteHeader() {
  const { locale, t } = await getI18n();
  const items: NavItem[] = [
    { href: `/${locale}`, label: t.nav.overview },
    { href: `/${locale}/stocks`, label: t.nav.stocks },
    { href: `/${locale}/shortlist`, label: t.nav.shortlist },
    { href: `/${locale}/radar`, label: t.nav.radar },
    { href: `/${locale}/brokers`, label: t.nav.brokers },
    { href: `/${locale}/ihsg`, label: t.nav.ihsg },
    { href: `/${locale}/watchlist`, label: t.nav.watchlist },
  ];
  return (
    <header className="border-b border-line bg-card">
      <div className="mx-auto max-w-6xl px-4 sm:px-8">
        <div className="flex items-center justify-between gap-3 py-3">
          <div className="flex items-center gap-6">
            <Link href={`/${locale}`} className="font-bold tracking-tight">
              {t.common.appName}
            </Link>
            {/* Wide screens: one line next to the name. */}
            <nav aria-label={t.nav.label} className="hidden gap-4 text-sm text-muted md:flex">
              <Suspense fallback={<NavList items={items} variant="bar" />}>
                <NavLinks items={items} variant="bar" />
              </Suspense>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <Suspense fallback={null}>
              <UserMenu />
            </Suspense>
            <LanguageSwitch locale={locale} labels={t.language} />
            <ThemeSwitch labels={t.theme} />
          </div>
        </div>
        {/* Phones: tappable pills on their own rows instead of squeezed text. */}
        <nav aria-label={t.nav.label} className="flex flex-wrap gap-2 pb-3 text-sm md:hidden">
          <Suspense fallback={<NavList items={items} variant="pills" />}>
            <NavLinks items={items} variant="pills" />
          </Suspense>
        </nav>
      </div>
    </header>
  );
}
