/** Small "HSC" tag; the title explains it on hover (and to screen readers). */
export function HscBadge({ label, title }: { label: string; title: string }) {
  return (
    <span
      title={title}
      aria-label={title}
      className="ml-1.5 rounded border border-warn-line bg-warn-bg px-1 py-px align-middle text-[10px] font-semibold text-warn-fg"
    >
      {label}
    </span>
  );
}
