import { useMemo } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Panel } from '../ui';
import { breakEvenSeries, toClock, waveSchedule, type RoamOptions, type Situation } from '../../lib/decision';

const SERIES = [
  { key: 'crash', name: 'Crashed wave', color: 'var(--teal)' },
  { key: 'neutral', name: 'Neutral wave', color: 'var(--blue)' },
  { key: 'losing', name: 'Wave pushing to you', color: 'var(--loss)' },
] as const;

export function BreakEvenChart({ s, o }: { s: Situation; o: RoamOptions }) {
  const data = useMemo(() => breakEvenSeries(s.role, o).map((r) => ({ ...r, crash: r.crash * 100, neutral: r.neutral * 100, losing: r.losing * 100 })), [s.role, o]);
  const lines = s.role === 'jungle' ? SERIES.slice(0, 1) : SERIES;
  const next = waveSchedule().filter((w) => w.t >= s.t - 1e-6).slice(0, 8);
  const firstCannon = next.find((w) => w.cannon);

  return (
    <Panel title="When does roaming pay off?" sub="Break-even takedown chance across the game · below a line = roam is worth it with that wave state">
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="t" type="number" domain={[2, 40]} ticks={[5, 10, 14, 20, 25, 30, 35, 40]} tickFormatter={(m) => `${m}'`} tickLine={false} axisLine={{ stroke: 'var(--border)' }} />
          <YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(v) => `${v}%`} tickLine={false} axisLine={false} width={44} />
          <Tooltip formatter={(v: number, n: string) => [`${Math.round(v)}%`, n]} labelFormatter={(m: number) => `${toClock(m)} · estimated levels`} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <ReferenceLine y={o.kill * 100} stroke="var(--gold)" strokeDasharray="6 4" label={{ value: `your chance ${Math.round(o.kill * 100)}%`, position: 'insideTopRight', fill: 'var(--gold)', fontSize: 11 }} />
          <ReferenceLine x={Math.min(40, Math.max(2, s.t))} stroke="var(--text-2)" label={{ value: `now ${toClock(s.t)}`, position: 'insideBottomRight', fill: 'var(--text-2)', fontSize: 11 }} />
          {lines.map((l) => (
            <Line key={l.key} type="stepAfter" dataKey={l.key} name={l.name} stroke={l.color} dot={false} strokeWidth={o.wave === l.key || s.role === 'jungle' ? 3 : 1.6} isAnimationActive={false} />
          ))}
        </LineChart>
      </ResponsiveContainer>
      <div className="section-title" style={{ marginTop: 14 }}>Wave clock <span className="note" style={{ textTransform: 'none', letterSpacing: 0 }}>· next waves from {toClock(s.t)} — shove a cannon wave, then roam</span></div>
      <div className="dm-waves">
        {next.map((w) => (
          <div key={w.n} className={`dm-wave ${w.cannon ? 'cannon' : ''} ${w === firstCannon && s.role !== 'jungle' ? 'go' : ''}`}>
            <b>{toClock(w.t)}</b>wave {w.n}{w.cannon ? ' · cannon' : ''}{w === firstCannon && s.role !== 'jungle' ? <em>shove → roam</em> : null}
          </div>
        ))}
      </div>
    </Panel>
  );
}
