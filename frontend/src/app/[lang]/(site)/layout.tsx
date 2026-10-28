import { SiteHeader } from "@/components/site-header";

/** Every page behind the login: the full header with the menu. */
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <SiteHeader />
      {children}
    </>
  );
}
