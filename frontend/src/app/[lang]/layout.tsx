import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { SiteFooter } from "@/components/site-footer";
import { getI18n } from "@/lib/i18n";
import { LOCALES } from "@/lib/i18n/locales";
import "../globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export function generateStaticParams() {
  return LOCALES.map((lang) => ({ lang }));
}

export async function generateMetadata(): Promise<Metadata> {
  const { t } = await getI18n();
  return { title: t.meta.title, description: t.meta.description };
}

export default async function RootLayout({ children }: LayoutProps<"/[lang]">) {
  const { locale } = await getI18n();
  return (
    <html
      lang={locale}
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      // The theme switch adds .light/.dark after hydration.
      suppressHydrationWarning
    >
      <body className="flex min-h-full flex-col bg-page text-fg">
        {/* The header comes from the group layouts: (site) has the full menu, (auth) only the name. */}
        {children}
        <SiteFooter />
      </body>
    </html>
  );
}
