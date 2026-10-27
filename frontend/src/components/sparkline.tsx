/**
 * Tiny trend line rendered on the server as SVG (no chart library, no JavaScript). Coloured by
 * the direction over the period; the numbers next to it carry the actual values.
 */
export function Sparkline({
  values,
  width = 80,
  height = 24,
  fluid = false,
}: {
  values: number[];
  width?: number;
  height?: number;
  /** Stretch to the container's width (the viewBox keeps the shape). */
  fluid?: boolean;
}) {
  if (values.length < 2) return <span className="inline-block" style={{ width, height }} aria-hidden />;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const pad = 2;
  const points = values
    .map((v, i) => {
      const x = pad + (i / (values.length - 1)) * (width - pad * 2);
      const y = pad + (1 - (v - min) / span) * (height - pad * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
  const last = values[values.length - 1];
  const color = last > values[0] ? "text-up" : last < values[0] ? "text-down" : "text-muted";
  return (
    <svg
      width={fluid ? "100%" : width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={color}
      aria-hidden
    >
      <polyline
        points={points}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
