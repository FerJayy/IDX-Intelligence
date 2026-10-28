import { NextResponse, type NextRequest } from "next/server";
import { DEFAULT_LOCALE, LOCALE_COOKIE, LOCALES, hasLocale, type Locale } from "@/lib/i18n/locales";

/** Pages a logged-out visitor may open; everything else needs an account. */
const PUBLIC_PAGES = ["login", "register", "forgot-password", "reset-password"];
/** Must match SESSION_COOKIE in lib/api.ts (not imported: the proxy stays free of server-only code). */
const SESSION_COOKIE = "idx_session";

// 1. Adds the language to URLs that lack one: /stocks/BBCA -> /id/stocks/BBCA.
//    Order: the language the visitor chose before (cookie), then the browser's language, then English.
// 2. Sends logged-out visitors to the login page. This only checks that a session cookie exists;
//    the backend validates it on every request (an expired one also ends at the login page).
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const prefix = LOCALES.find((l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`));
  if (prefix) {
    const page = pathname.split("/")[2] ?? "";
    if (PUBLIC_PAGES.includes(page) || request.cookies.has(SESSION_COOKIE)) return;
    const login = request.nextUrl.clone();
    login.pathname = `/${prefix}/login`;
    login.search = pathname === `/${prefix}` ? "" : `?next=${encodeURIComponent(pathname + request.nextUrl.search)}`;
    return NextResponse.redirect(login);
  }

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
