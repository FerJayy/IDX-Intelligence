export function DemoBanner({ notice }: { notice: string }) {
  return (
    <div className="rounded-lg border border-warn-line bg-warn-bg px-4 py-3 text-center text-sm font-semibold tracking-wide text-warn-fg">
      {notice}
    </div>
  );
}
