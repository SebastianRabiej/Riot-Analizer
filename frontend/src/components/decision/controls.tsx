import type { ReactNode } from 'react';

/** Labelled range input in the app's style. */
export function Slider({
  label, value, min, max, step = 1, onChange, format = (v) => String(v), hint,
}: {
  label: string; value: number; min: number; max: number; step?: number;
  onChange: (v: number) => void; format?: (v: number) => string; hint?: ReactNode;
}) {
  return (
    <label className="dm-field">
      <span className="dm-label">{label}<b>{format(value)}</b></span>
      <input type="range" min={min} max={max} step={step} value={value} onChange={(e) => onChange(+e.target.value)} />
      {hint && <span className="note">{hint}</span>}
    </label>
  );
}

/** Labelled native select. */
export function Select<T extends string | number>({
  label, value, options, onChange, hint,
}: {
  label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; hint?: ReactNode;
}) {
  return (
    <label className="dm-field">
      <span className="dm-label">{label}</span>
      <select
        className="dm-input"
        value={String(value)}
        onChange={(e) => {
          const hit = options.find((o) => String(o.value) === e.target.value);
          if (hit) onChange(hit.value);
        }}
      >
        {options.map((o) => <option key={String(o.value)} value={String(o.value)}>{o.label}</option>)}
      </select>
      {hint && <span className="note">{hint}</span>}
    </label>
  );
}

export function Toggle({ checked, onChange, title, sub }: { checked: boolean; onChange: (v: boolean) => void; title: string; sub?: ReactNode }) {
  return (
    <label className="dm-toggle">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span><strong>{title}</strong>{sub && <small>{sub}</small>}</span>
    </label>
  );
}

export const fmt = (n: number, digits = 1) => Number(n).toLocaleString('en-US', { maximumFractionDigits: digits });
export const gold = (n: number) => `${Math.round(n).toLocaleString('en-US')}g`;
export const xp1 = (n: number) => fmt(Math.round(n * 10) / 10, 1);
export const pctTxt = (v: number) => (v > 1 ? '>100%' : `${Math.round(v * 100)}%`);
