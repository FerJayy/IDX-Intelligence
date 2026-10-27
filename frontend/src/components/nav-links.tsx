"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export type NavItem = { href: string; label: string };

/** Highlights the current section. Reads the URL, so the header renders it inside <Suspense>. */
export function NavLinks({ items, variant }: { items: NavItem[]; variant: "bar" | "pills" }) {
  const pathname = usePathname();
  return <NavList items={items} variant={variant} active={activeHref(items, pathname)} />;
}

/** Same links without a highlight: the Suspense fallback. */
export function NavList({ items, variant, active }: { items: NavItem[]; variant: "bar" | "pills"; active?: string }) {
  return (
    <>
      {items.map((item) => {
        const current = item.href === active;
        const className =
          variant === "bar"
            ? `hover:text-fg ${current ? "font-semibold text-fg" : ""}`
            : `rounded-full border px-3 py-1.5 ${current ? "border-fg bg-chip font-semibold text-fg" : "border-line text-muted"}`;
        return (
          <Link key={item.href} href={item.href} aria-current={current ? "page" : undefined} className={className}>
            {item.label}
          </Link>
        );
      })}
    </>
  );
}

/** The longest matching prefix wins, so /en/stocks/BBCA highlights Stocks and /en only Overview. */
function activeHref(items: NavItem[], pathname: string): string | undefined {
  return items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href;
}
