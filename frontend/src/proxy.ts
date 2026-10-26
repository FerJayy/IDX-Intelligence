import { NextResponse, type NextRequest } from "next/server";
import { DEFAULT_LOCALE, LOCALE_COOKIE, LOCALES, hasLocale, type Locale } from "@/lib/i18n/locales";

// Adds the language to URLs that lack one: /stocks/BBCA -> /id/stocks/BBCA.
// Order: the language the visitor chose before (cookie), then the browser's language, then English.
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasPrefix = LOCALES.some((l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`));
  if (hasPrefix) return;

  request.nextUrl.pathname = `/${preferredLocale(request)}${pathname === "/" ? "" : pathname}`;
  return NextResponse.redirect(request.nextUrl);
}

function preferredLocale(request: NextRequest): Locale {
  const saved = request.cookies.get(LOCALE_COOKIE)?.value;
  if (hasLocale(saved)) return saved;
  const accept = request.headers.get("accept-language") ?? "";
  // e.g. "id-ID,id;q=0.9,en;q=0.8": pick the first supported language in the list.
  for (const part of accept.split(",")) {
    const code = part.split(";")[0].trim().toLowerCase().split("-")[0];
    if (hasLocale(code)) return code;
  }
  return DEFAULT_LOCALE;
}

export const config = {
  // Skip Next internals, API routes and files with an extension (favicon.ico, images, ...).
  matcher: ["/((?!_next|api|.*\\..*).*)"],
};
