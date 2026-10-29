import { getI18n } from "@/lib/i18n";

const AUTHOR = "Fernando Jason";
const EMAIL = "fernandojason100@gmail.com";
const LINKEDIN = "https://www.linkedin.com/in/fernando-jasonn";

/** Who made the site and how to reach them; shown on every page, including login. */
export async function SiteFooter() {
  const { t } = await getI18n();
  const f = t.footer;
  return (
    <footer className="border-t border-line px-4 py-6 text-xs text-faint sm:px-8">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p>
          {f.createdBy} <span className="font-semibold text-muted">{AUTHOR}</span> · {f.notAdvice}
        </p>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span>{f.contact}</span>
          <a href={`mailto:${EMAIL}`} className="break-all text-muted hover:text-accent hover:underline">
            {f.email}: {EMAIL}
          </a>
          <a
            href={LINKEDIN}
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted hover:text-accent hover:underline"
          >
            {f.linkedin}: fernando-jasonn
          </a>
        </p>
      </div>
    </footer>
  );
}
