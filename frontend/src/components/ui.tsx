import type { ReactNode } from 'react';
import { round, wrClass } from '../util';

/* -------------------------------------------------- states */

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state">
      <div className="spinner" />
      <div>{label}</div>
    </div>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return (
    <div className="state error">
      <div className="emoji">⚠️</div>
      <div>{message}</div>
    </div>
  );
}

export function EmptyState({
  emoji = '📭',
  message,
  hint,
}: {
  emoji?: string;
  message: string;
  hint?: string;
}) {
  return (
    <div className="state">
      <div className="emoji">{emoji}</div>
      <div>{message}</div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  );
}

/* -------------------------------------------------- panel */

export function Panel({
  title,
  sub,
  action,
  children,
  className = '',
}: {
  title?: ReactNode;
  sub?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel ${className}`}>
      {(title || action) && (
        <div className="panel-head">
          <div>
            {title && <h3>{title}</h3>}
            {sub && <div className="sub">{sub}</div>}
          </div>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

/* -------------------------------------------------- stat tile */

export function StatTile({
  label,
  value,
  sub,
  tone,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  tone?: 'win' | 'loss';
}) {
  return (
    <div className="tile">
      <div className="tlabel">{label}</div>
      <div className={`tvalue ${tone ?? ''}`}>{value}</div>
      {sub != null && <div className="tsub">{sub}</div>}
    </div>
  );
}

/* -------------------------------------------------- win-rate bar */

export function WinRateBar({
  wins,
  losses,
  showText = true,
}: {
  wins: number;
  losses: number;
  showText?: boolean;
}) {
  const total = wins + losses;
  const wr = total > 0 ? (wins / total) * 100 : 0;
  return (
    <div className="wrbar-wrap">
      <div className="wrbar" style={{ flex: 1 }} title={`${wins}W ${losses}L`}>
        <div className="win-seg" style={{ width: `${wr}%` }} />
      </div>
      {showText && (
        <span className="wl tnum">
          {wins}W {losses}L · <b className={`wr-strong ${wrClass(wr)}`}>{round(wr)}%</b>
        </span>
      )}
    </div>
  );
}

/** Compact inline win-rate bar (no text), coloured by quality. */
export function MiniWr({ winRate }: { winRate: number }) {
  const cls = wrClass(winRate);
  const color = cls === 'good' ? 'var(--win)' : cls === 'bad' ? 'var(--loss)' : 'var(--gold)';
  return (
    <div className="mini-bar" title={`${round(winRate)}%`}>
      <span style={{ width: `${Math.max(0, Math.min(100, winRate))}%`, background: color }} />
    </div>
  );
}

/* -------------------------------------------------- win-rate ring (SVG donut) */

export function WinRateRing({
  winRate,
  size = 118,
  stroke = 11,
  center,
}: {
  winRate: number;
  size?: number;
  stroke?: number;
  center?: ReactNode;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const cls = wrClass(winRate);
  const color = cls === 'good' ? 'var(--win)' : cls === 'bad' ? 'var(--loss)' : 'var(--gold)';
  const off = c * (1 - Math.max(0, Math.min(100, winRate)) / 100);
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--panel-3)" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeDasharray={c}
          strokeDashoffset={off}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ transition: 'stroke-dashoffset .6s ease' }}
        />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'grid',
          placeItems: 'center',
          textAlign: 'center',
        }}
      >
        {center ?? (
          <div>
            <div style={{ fontSize: 26, fontWeight: 800 }} className={`wr-strong ${cls}`}>
              {round(winRate)}%
            </div>
            <div className="note">win rate</div>
          </div>
        )}
      </div>
    </div>
  );
}

/* -------------------------------------------------- form pips */

export function FormPips({ form, max }: { form: boolean[]; max?: number }) {
  if (!form || form.length === 0) return <span className="note">No recent games</span>;
  const shown = max ? form.slice(0, max) : form;
  return (
    <div className="pips">
      {shown.map((win, i) => (
        <span key={i} className={`pip ${win ? 'w' : 'l'}`} title={win ? 'Win' : 'Loss'}>
          {win ? 'W' : 'L'}
        </span>
      ))}
    </div>
  );
}

/* -------------------------------------------------- segmented control */

export interface SegOption<T extends string | number> {
  value: T;
  label: ReactNode;
}

export function Segmented<T extends string | number>({
  options,
  value,
  onChange,
  teal = true,
}: {
  options: SegOption<T>[];
  value: T;
  onChange: (v: T) => void;
  teal?: boolean;
}) {
  return (
    <div className={`segmented ${teal ? 'teal' : ''}`}>
      {options.map((o) => (
        <button
          key={String(o.value)}
          className={o.value === value ? 'active' : ''}
          onClick={() => onChange(o.value)}
          type="button"
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
