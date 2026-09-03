import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BarChart,
  Bar,
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ScatterChart,
  Scatter,
} from 'recharts';
import type { CarryIndexDto, GameLengthStats, PerfPoint, PerformanceTrend, TimeBucketDto } from '../types';
import { getCarry, getGameLength, getPerformance, getRoles, getTimeStats } from '../api';
import { useAsync, useChampionNames, type AsyncState } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, WinRateBar, WinRateRing, StatTile, MiniWr, Segmented } from '../components/ui';
import { dateShort, formatDuration, positionIcon, positionLabel, round, wrClass } from '../util';

/* -------------------------------------------------- shared filter styling */

const selectStyle: React.CSSProperties = {
  background: 'var(--panel-2)',
  color: 'var(--text)',
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
};

/* -------------------------------------------------- time charts */

function barColor(winRate: number, games: number): string {
  if (games === 0) return 'var(--panel-3)';
  const c = wrClass(winRate);
  return c === 'good' ? 'var(--win)' : c === 'bad' ? 'var(--loss)' : 'var(--gold)';
}

interface TipPayload { payload: { label: string; winRate: number; games: number } }
function TimeTooltip({ active, payload }: { active?: boolean; payload?: TipPayload[] }) {
  if (!active || !payload || !payload.length) return null;
  const p = payload[0].payload;
  if (p.games === 0) return <div className="recharts-default-tooltip" style={{ padding: '6px 10px' }}><b>{p.label}</b><div className="note">no games</div></div>;
  return (
    <div className="recharts-default-tooltip" style={{ padding: '6px 10px' }}>
      <b>{p.label}</b>
      <div className="tnum">{round(p.winRate)}% · {p.games} games</div>
    </div>
  );
}

function TimeChart({ buckets, height = 170 }: { buckets: TimeBucketDto[]; height?: number }) {
  const data = buckets.map((b) => ({ label: b.label, winRate: b.winRate, games: b.games }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 6, right: 8, bottom: 0, left: 0 }}>
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: 'var(--border)' }} interval="preserveStartEnd" />
        <YAxis domain={[0, 100]} ticks={[0, 50, 100]} tickLine={false} axisLine={false} width={40} />
        <ReferenceLine y={50} stroke="var(--border-2)" strokeDasharray="3 3" />
        <Tooltip content={<TimeTooltip />} cursor={{ fill: 'rgba(255,255,255,0.03)' }} />
        <Bar dataKey="winRate" radius={[3, 3, 0, 0]}>
          {data.map((d, i) => <Cell key={i} fill={barColor(d.winRate, d.games)} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/* -------------------------------------------------- performance over time */

interface Metric {
  key: string;
  label: string;
  digits: number;
  get: (p: PerfPoint) => number;
}

const perMin = (total: number, durSec: number) => (durSec > 0 ? total / (durSec / 60) : 0);

const METRICS: Metric[] = [
  { key: 'kda', label: 'KDA', digits: 2, get: (p) => (p.deaths === 0 ? p.kills + p.assists : (p.kills + p.assists) / p.deaths) },
  { key: 'csmin', label: 'CS/min', digits: 1, get: (p) => p.csPerMin },
  { key: 'dmgmin', label: 'Dmg/min', digits: 0, get: (p) => perMin(p.damageToChampions, p.gameDurationSec) },
  { key: 'vision', label: 'Vision', digits: 1, get: (p) => p.visionScore },
  { key: 'goldmin', label: 'Gold/min', digits: 0, get: (p) => perMin(p.goldEarned, p.gameDurationSec) },
];

function movingAvg(vals: number[], w: number): number[] {
  const out: number[] = [];
  const q: number[] = [];
  let sum = 0;
  for (const v of vals) {
    q.push(v);
    sum += v;
    if (q.length > w) sum -= q.shift() as number;
    out.push(sum / q.length);
  }
  return out;
}

function linreg(vals: number[]): { slope: number; intercept: number } {
  const n = vals.length;
  if (n < 2) return { slope: 0, intercept: n ? vals[0] : 0 };
  let sx = 0, sy = 0, sxx = 0, sxy = 0;
  for (let i = 0; i < n; i++) {
    sx += i; sy += vals[i]; sxx += i * i; sxy += i * vals[i];
  }
  const d = n * sxx - sx * sx;
  const slope = d === 0 ? 0 : (n * sxy - sx * sy) / d;
  const intercept = (sy - slope * sx) / n;
  return { slope, intercept };
}

interface PerfDatum {
  idx: number;
  date: number;
  value: number;
  avg: number;
  win: boolean;
  champ: string;
  pos: string;
  k: number; d: number; a: number;
  matchId: string;
}

function PerfTooltip({
  active, payload, metric,
}: { active?: boolean; payload?: { payload: PerfDatum }[]; metric: Metric }) {
  if (!active || !payload || !payload.length) return null;
  const p = payload[0].payload;
  return (
    <div className="recharts-default-tooltip" style={{ padding: '8px 11px' }}>
      <b>
        <span style={{ color: p.win ? 'var(--win)' : 'var(--loss)' }}>{p.win ? 'W' : 'L'}</span>{' '}
        {positionIcon(p.pos)} {p.champ}
      </b>
      <div className="tnum">{metric.label}: <b>{round(p.value, metric.digits)}</b></div>
      <div className="note tnum">
        {p.k}/{p.d}/{p.a} · {dateShort(p.date)}
      </div>
      <div className="note" style={{ fontSize: 11 }}>click to open game →</div>
    </div>
  );
}

function PerformancePanel({
  perf, champ, role,
}: { perf: AsyncState<PerformanceTrend>; champ: string; role: string }) {
  const { gameName, tagLine } = usePlayerCtx();
  const navigate = useNavigate();
  const [metricKey, setMetricKey] = useState('csmin');
  const metric = METRICS.find((m) => m.key === metricKey) ?? METRICS[0];
  const data = perf.data;

  const { chart, trend } = useMemo(() => {
    if (!data) return { chart: [] as PerfDatum[], trend: null as null | { first: number; last: number } };
    let filtered = data.points;
    if (champ) filtered = filtered.filter((p) => p.championName === champ);
    if (role) filtered = filtered.filter((p) => (p.teamPosition ?? '').toUpperCase() === role);
    const values = filtered.map(metric.get);
    const n = values.length;
    const w = Math.min(15, Math.max(3, Math.floor(n / 5)));
    const avg = movingAvg(values, w);
    const rows: PerfDatum[] = filtered.map((p, i) => ({
      idx: i,
      date: p.gameCreation,
      value: round(values[i], metric.digits),
      avg: round(avg[i], metric.digits),
      win: p.win,
      champ: p.championName,
      pos: p.teamPosition,
      k: p.kills, d: p.deaths, a: p.assists,
      matchId: p.matchId,
    }));
    const { slope, intercept } = linreg(values);
    const trendInfo = n >= 2 ? { first: intercept, last: intercept + slope * (n - 1) } : null;
    return { chart: rows, trend: trendInfo };
  }, [data, champ, role, metric]);

  const openMatch = (matchId: string) => {
    navigate(`/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}/matches/${encodeURIComponent(matchId)}`);
  };

  const renderDot = (props: { cx?: number; cy?: number; payload?: PerfDatum }) => {
    const { cx, cy, payload } = props;
    if (cx == null || cy == null || !payload) return <g key={Math.random()} />;
    return (
      <circle
        key={payload.idx}
        cx={cx}
        cy={cy}
        r={2.8}
        fill={payload.win ? 'var(--win)' : 'var(--loss)'}
        style={{ cursor: 'pointer' }}
        onClick={() => openMatch(payload.matchId)}
      />
    );
  };

  return (
    <Panel
      title="Performance over time"
      sub="Each dot is a game (green win, red loss). The gold line is your rolling average — the trend."
      action={
        <Segmented
          options={METRICS.map((m) => ({ value: m.key, label: m.label }))}
          value={metricKey}
          onChange={setMetricKey}
        />
      }
    >
      {perf.loading && !data && <Loading />}
      {perf.error && <ErrorBox message={perf.error} />}
      {data && data.totalGames === 0 && <EmptyState emoji="📈" message="No games yet" />}

      {data && data.totalGames > 0 && (
        <div className="stack" style={{ gap: 12 }}>
          {trend && (
            <div className="note tnum">
              {metric.label} trend:{' '}
              <b className={`wr-strong ${trend.last >= trend.first ? 'good' : 'bad'}`}>
                {round(trend.first, metric.digits)} → {round(trend.last, metric.digits)}
              </b>{' '}
              over {chart.length} games
            </div>
          )}

          {chart.length >= 2 ? (
            <ResponsiveContainer width="100%" height={280}>
              <ComposedChart data={chart} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="idx"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tickLine={false}
                  axisLine={{ stroke: 'var(--border)' }}
                  tickFormatter={(i: number) => (chart[i] ? dateShort(chart[i].date) : '')}
                  interval="preserveStartEnd"
                  minTickGap={44}
                />
                <YAxis tickLine={false} axisLine={false} width={44} />
                <Tooltip content={<PerfTooltip metric={metric} />} cursor={{ stroke: 'var(--border-2)' }} />
                <Line
                  dataKey="value"
                  stroke="var(--border-2)"
                  strokeWidth={1}
                  dot={renderDot}
                  activeDot={{ r: 5 }}
                  isAnimationActive={false}
                />
                <Line
                  dataKey="avg"
                  type="monotone"
                  stroke="var(--gold)"
                  strokeWidth={2.5}
                  dot={false}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState emoji="📉" message="Not enough games for this filter" hint="Widen the champion or role filter above." />
          )}
        </div>
      )}
    </Panel>
  );
}

/* -------------------------------------------------- win factors (what wins your games) */

interface CorrMetric {
  key: string;
  label: string;
  unit: string;
  digits: number;
  get: (p: PerfPoint) => number;
}

const CORR_METRICS: CorrMetric[] = [
  { key: 'kda', label: 'KDA', unit: '', digits: 2, get: (p) => (p.deaths === 0 ? p.kills + p.assists : (p.kills + p.assists) / p.deaths) },
  { key: 'csmin', label: 'CS/min', unit: '', digits: 1, get: (p) => p.csPerMin },
  { key: 'dmgmin', label: 'Dmg/min', unit: '', digits: 0, get: (p) => perMin(p.damageToChampions, p.gameDurationSec) },
  { key: 'goldmin', label: 'Gold/min', unit: '', digits: 0, get: (p) => perMin(p.goldEarned, p.gameDurationSec) },
  { key: 'vision', label: 'Vision', unit: '', digits: 1, get: (p) => p.visionScore },
  { key: 'kills', label: 'Kills', unit: '', digits: 1, get: (p) => p.kills },
  { key: 'deaths', label: 'Deaths', unit: '', digits: 1, get: (p) => p.deaths },
];

const MIN_CORR_GAMES = 12;

interface Factor {
  key: string;
  label: string;
  unit: string;
  digits: number;
  lowWR: number;
  highWR: number;
  delta: number;
  threshold: number;
  half: number;
}

/**
 * Split the games at the median of each stat into a low half and a high half and
 * compare win rate. Balanced halves (by rank, so ties never skew the sizes) keep
 * the sample even. delta = highWR - lowWR, so a big positive delta means "more of
 * this stat, more wins"; negative (e.g. Deaths) means the opposite.
 */
function computeFactors(points: PerfPoint[]): { factors: Factor[]; n: number; overall: number } {
  const n = points.length;
  const overall = n ? (points.filter((p) => p.win).length / n) * 100 : 0;
  if (n < MIN_CORR_GAMES) return { factors: [], n, overall };
  const half = Math.floor(n / 2);
  const factors: Factor[] = [];
  for (const m of CORR_METRICS) {
    const rows = points.map((p) => ({ v: m.get(p), win: p.win })).sort((a, b) => a.v - b.v);
    const low = rows.slice(0, half);
    const high = rows.slice(n - half);
    const lowWR = (low.filter((r) => r.win).length / half) * 100;
    const highWR = (high.filter((r) => r.win).length / half) * 100;
    const threshold = rows[n - half].v; // smallest value that lands in the high group
    factors.push({ key: m.key, label: m.label, unit: m.unit, digits: m.digits, lowWR, highWR, delta: highWR - lowWR, threshold, half });
  }
  factors.sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));
  return { factors, n, overall };
}

const clampPct = (x: number) => Math.max(0, Math.min(100, x));

function FactorRow({ f }: { f: Factor }) {
  const up = f.delta >= 0;
  const color = up ? 'var(--win)' : 'var(--loss)';
  const lo = clampPct(Math.min(f.lowWR, f.highWR));
  const hi = clampPct(Math.max(f.lowWR, f.highWR));
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '116px 1fr 78px',
        alignItems: 'center',
        gap: 12,
        padding: '9px 0',
        borderTop: '1px solid var(--border)',
      }}
    >
      <div>
        <b>{f.label}</b>
        <div className="note" style={{ fontSize: 11 }}>high &ge; {round(f.threshold, f.digits)}{f.unit}</div>
      </div>
      <div style={{ position: 'relative', height: 18 }} title={`Low half: ${Math.round(f.lowWR)}% win · High half: ${Math.round(f.highWR)}%`}>
        <div style={{ position: 'absolute', top: 7, left: 0, right: 0, height: 4, background: 'var(--panel-3)', borderRadius: 2 }} />
        <div style={{ position: 'absolute', top: 1, left: '50%', width: 1, height: 16, background: 'var(--border-2)' }} />
        <div style={{ position: 'absolute', top: 7, left: `${lo}%`, width: `${hi - lo}%`, height: 4, background: color, borderRadius: 2 }} />
        <div style={{ position: 'absolute', top: 4, left: `${clampPct(f.lowWR)}%`, transform: 'translateX(-50%)', width: 10, height: 10, borderRadius: '50%', background: 'var(--text-3)', border: '2px solid var(--panel)' }} />
        <div style={{ position: 'absolute', top: 3, left: `${clampPct(f.highWR)}%`, transform: 'translateX(-50%)', width: 12, height: 12, borderRadius: '50%', background: color, border: '2px solid var(--panel)' }} />
      </div>
      <div style={{ textAlign: 'right' }}>
        <div className={`wr-strong ${up ? 'good' : 'bad'}`} style={{ fontWeight: 800 }}>{up ? '+' : ''}{Math.round(f.delta)}%</div>
        <div className="note tnum" style={{ fontSize: 11 }}>{Math.round(f.lowWR)}&rarr;{Math.round(f.highWR)}%</div>
      </div>
    </div>
  );
}

function WinFactorsPanel({
  perf, champ, role,
}: { perf: AsyncState<PerformanceTrend>; champ: string; role: string }) {
  const data = perf.data;
  const { factors, n } = useMemo(() => {
    if (!data) return { factors: [] as Factor[], n: 0, overall: 0 };
    let pts = data.points;
    if (champ) pts = pts.filter((p) => p.championName === champ);
    if (role) pts = pts.filter((p) => (p.teamPosition ?? '').toUpperCase() === role);
    return computeFactors(pts);
  }, [data, champ, role]);

  const top = factors.length ? factors[0] : null;

  return (
    <Panel
      title="What wins your games?"
      sub="Your win rate when a stat runs high vs low, split at your own median. Shows what your wins line up with — association, not proof."
    >
      {perf.loading && !data && <Loading />}
      {perf.error && <ErrorBox message={perf.error} />}
      {data && n < MIN_CORR_GAMES && (
        <EmptyState emoji="🧪" message={`Need at least ${MIN_CORR_GAMES} games for this filter`} hint="Widen the champion or role filter above." />
      )}
      {data && n >= MIN_CORR_GAMES && (
        <div className="stack" style={{ gap: 12 }}>
          {top && Math.abs(top.delta) >= 4 && (
            <div style={{ fontSize: 13.5 }}>
              Your wins track most with <b>{top.label}</b>:{' '}
              <b className="wr-strong good">{Math.round(top.delta >= 0 ? top.highWR : top.lowWR)}%</b> when it&apos;s {top.delta >= 0 ? 'high' : 'low'}
              {' '}vs <b className="wr-strong bad">{Math.round(top.delta >= 0 ? top.lowWR : top.highWR)}%</b> when {top.delta >= 0 ? 'low' : 'high'}.
            </div>
          )}
          <div>
            {factors.map((f) => <FactorRow key={f.key} f={f} />)}
          </div>
          <div className="note" style={{ fontSize: 11 }}>
            Based on {n} games, split into two halves of {top?.half}. The dot pair is your win rate in the low half (grey) vs the high half (coloured); the 50% mark is the centre tick. Higher is not always better — Deaths, for one, wins less when high.{n < 25 ? ' Small sample, so treat this as a hint.' : ''}
          </div>
        </div>
      )}
    </Panel>
  );
}

/* -------------------------------------------------- carry index */

interface CarryDatum {
  idx: number;
  date: number;
  carryScore: number;
  win: boolean;
  champ: string;
  pos: string;
  top: boolean;
  matchId: string;
}

function CarryScatterTooltip({ active, payload }: { active?: boolean; payload?: { payload: CarryDatum }[] }) {
  if (!active || !payload || !payload.length) return null;
  const p = payload[0].payload;
  return (
    <div className="recharts-default-tooltip" style={{ padding: '8px 11px' }}>
      <b>
        <span style={{ color: p.win ? 'var(--win)' : 'var(--loss)' }}>{p.win ? 'W' : 'L'}</span>{' '}
        {positionIcon(p.pos)} {p.champ}
      </b>
      <div className="tnum">Carry score: <b>{round(p.carryScore)}</b>{p.top ? ' · team top dmg' : ''}</div>
      <div className="note tnum">{dateShort(p.date)}</div>
      <div className="note" style={{ fontSize: 11 }}>click to open game &rarr;</div>
    </div>
  );
}

function CarryPanel({ carry }: { carry: AsyncState<CarryIndexDto> }) {
  const { gameName, tagLine } = usePlayerCtx();
  const navigate = useNavigate();
  const data = carry.data;

  const scatter = useMemo<CarryDatum[]>(() => {
    if (!data) return [];
    return data.perGame.map((p, i) => ({
      idx: i,
      date: p.gameCreation,
      carryScore: p.carryScore,
      win: p.win,
      champ: p.championName,
      pos: p.teamPosition,
      top: p.topDamage,
      matchId: p.matchId,
    }));
  }, [data]);

  const openMatch = (matchId: string) => {
    navigate(`/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}/matches/${encodeURIComponent(matchId)}`);
  };

  return (
    <Panel
      title="Carry Index"
      sub="How much of your team's output is you — damage, gold and kill participation blended. 50 is an even one-fifth share; higher means you carry."
    >
      {carry.loading && !data && <Loading />}
      {carry.error && <ErrorBox message={carry.error} />}
      {data && data.games === 0 && <EmptyState emoji="🏋️" message="No games for this filter" />}

      {data && data.games > 0 && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="row" style={{ gap: 20, flexWrap: 'wrap', alignItems: 'center' }}>
            <WinRateRing
              winRate={data.carryIndex}
              center={
                <div>
                  <div style={{ fontSize: 28, fontWeight: 800 }} className={`wr-strong ${wrClass(data.carryIndex)}`}>
                    {round(data.carryIndex)}
                  </div>
                  <div className="note">carry index</div>
                </div>
              }
            />
            <div className="grid cols-2" style={{ flex: 1, minWidth: 240, gap: 10 }}>
              <StatTile label="Damage share" value={`${round(data.avgDamageShare)}%`} sub="of your team's damage" />
              <StatTile label="Gold share" value={`${round(data.avgGoldShare)}%`} sub="of your team's gold" />
              <StatTile label="Kill participation" value={`${round(data.avgKillParticipation)}%`} sub="kills+assists / team kills" />
              <StatTile label="Top damage" value={`${round(data.topDamageRate)}%`} sub="of games you led your team" />
            </div>
          </div>

          <div className="dash">
            <div className="tile" style={{ padding: 14 }}>
              <div className="row between">
                <b>Win rate when you carry</b>
                <span className={`wr-strong ${wrClass(data.winRateWhenTopDamage)}`} style={{ fontWeight: 800 }}>
                  {round(data.winRateWhenTopDamage)}%
                </span>
              </div>
              <div style={{ marginTop: 8 }}><MiniWr winRate={data.winRateWhenTopDamage} /></div>
              <div className="note" style={{ fontSize: 11, marginTop: 6 }}>games you were your team's top damage</div>
            </div>
            <div className="tile" style={{ padding: 14 }}>
              <div className="row between">
                <b>Win rate otherwise</b>
                <span className={`wr-strong ${wrClass(data.winRateOtherwise)}`} style={{ fontWeight: 800 }}>
                  {round(data.winRateOtherwise)}%
                </span>
              </div>
              <div style={{ marginTop: 8 }}><MiniWr winRate={data.winRateOtherwise} /></div>
              <div className="note" style={{ fontSize: 11, marginTop: 6 }}>games a teammate led in damage</div>
            </div>
          </div>

          {scatter.length >= 2 && (
            <div>
              <div className="section-title">Carry score per game (green win, red loss)</div>
              <ResponsiveContainer width="100%" height={180}>
                <ScatterChart margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
                  <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="idx"
                    type="number"
                    domain={['dataMin', 'dataMax']}
                    tickLine={false}
                    axisLine={{ stroke: 'var(--border)' }}
                    tickFormatter={(i: number) => (scatter[i] ? dateShort(scatter[i].date) : '')}
                    interval="preserveStartEnd"
                    minTickGap={44}
                  />
                  <YAxis dataKey="carryScore" domain={[0, 100]} ticks={[0, 50, 100]} tickLine={false} axisLine={false} width={40} />
                  <ReferenceLine y={50} stroke="var(--border-2)" strokeDasharray="3 3" />
                  <Tooltip content={<CarryScatterTooltip />} cursor={{ stroke: 'var(--border-2)' }} />
                  <Scatter
                    data={scatter}
                    onClick={(node: { matchId?: string; payload?: CarryDatum }) => {
                      const m = node?.matchId ?? node?.payload?.matchId;
                      if (m) openMatch(m);
                    }}
                  >
                    {scatter.map((d, i) => (
                      <Cell key={i} fill={d.win ? 'var(--win)' : 'var(--loss)'} style={{ cursor: 'pointer' }} />
                    ))}
                  </Scatter>
                </ScatterChart>
              </ResponsiveContainer>
            </div>
          )}

          {data.games < 8 && (
            <div className="note" style={{ fontSize: 11 }}>Small sample ({data.games} games) — treat this as a hint.</div>
          )}
        </div>
      )}
    </Panel>
  );
}

/* -------------------------------------------------- game length */

function GameLengthPanel({ gameLen }: { gameLen: AsyncState<GameLengthStats> }) {
  const data = gameLen.data;
  const faster = data ? data.avgWinDurationSec > 0 && data.avgLossDurationSec > 0
    && data.avgWinDurationSec < data.avgLossDurationSec : false;

  return (
    <Panel
      title="Win rate by game length"
      sub="Do you stomp early and fade, or grind out the long ones? Bars are green above 50%, red below."
    >
      {gameLen.loading && !data && <Loading />}
      {gameLen.error && <ErrorBox message={gameLen.error} />}
      {data && data.total === 0 && <EmptyState emoji="⏱️" message="No games for this filter" />}

      {data && data.total > 0 && (
        <div className="stack" style={{ gap: 14 }}>
          <TimeChart buckets={data.buckets} />
          <div className="dash">
            <StatTile
              label="Avg win length"
              value={data.avgWinDurationSec > 0 ? formatDuration(Math.round(data.avgWinDurationSec)) : '—'}
              tone="win"
            />
            <StatTile
              label="Avg loss length"
              value={data.avgLossDurationSec > 0 ? formatDuration(Math.round(data.avgLossDurationSec)) : '—'}
              tone="loss"
            />
          </div>
          {data.avgWinDurationSec > 0 && data.avgLossDurationSec > 0 && (
            <div className="note" style={{ fontSize: 12.5 }}>
              Your wins run{' '}
              <b className={`wr-strong ${faster ? 'good' : 'bad'}`}>
                {faster ? 'shorter' : 'longer'}
              </b>{' '}
              than your losses on average ({formatDuration(Math.round(data.avgWinDurationSec))} vs{' '}
              {formatDuration(Math.round(data.avgLossDurationSec))}) — {faster
                ? 'you tend to close out games you get ahead in.'
                : 'you tend to win the grind and lose faster when behind.'}
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}

/* -------------------------------------------------- page */

export function Trends() {
  const { gameName, tagLine, queue, refreshKey } = usePlayerCtx();
  const tz = new Date().getTimezoneOffset();
  const [champ, setChamp] = useState(''); // '' = all champions (value is the API championName)
  const [role, setRole] = useState(''); // '' = all roles

  const champNames = useChampionNames();
  const nameMap = useMemo(() => {
    const m = new Map<string, string>();
    champNames.forEach((c) => m.set(c.id, c.name));
    return m;
  }, [champNames]);

  const perf = useAsync(() => getPerformance(gameName, tagLine, queue), [gameName, tagLine, queue, refreshKey]);

  // Champion + role options come from the full season points (perf endpoint).
  const champOptions = useMemo(() => {
    if (!perf.data) return [] as { id: string; label: string; games: number }[];
    const counts = new Map<string, number>();
    for (const p of perf.data.points) {
      if (!p.championName) continue;
      counts.set(p.championName, (counts.get(p.championName) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([id, games]) => ({ id, label: nameMap.get(id) ?? id, games }))
      .sort((a, b) => b.games - a.games || a.label.localeCompare(b.label));
  }, [perf.data, nameMap]);

  const roleOptions = useMemo(() => {
    const opts: { value: string; label: string }[] = [{ value: '', label: 'All roles' }];
    if (!perf.data) return opts;
    const seen = new Set<string>();
    for (const p of perf.data.points) {
      if (champ && p.championName !== champ) continue;
      const pos = (p.teamPosition ?? '').toUpperCase();
      if (pos) seen.add(pos);
    }
    ['TOP', 'JUNGLE', 'MIDDLE', 'BOTTOM', 'UTILITY'].forEach((r) => {
      if (seen.has(r)) opts.push({ value: r, label: positionLabel(r) });
    });
    return opts;
  }, [perf.data, champ]);

  // If the chosen role isn't valid for the chosen champion, fall back to all roles.
  const effectiveRole = roleOptions.some((o) => o.value === role) ? role : '';

  const champParam = champ || undefined;
  const roleParam = effectiveRole || undefined;

  const roles = useAsync(
    () => getRoles(gameName, tagLine, queue, champParam, roleParam),
    [gameName, tagLine, queue, champParam, roleParam, refreshKey],
  );
  const time = useAsync(
    () => getTimeStats(gameName, tagLine, tz, queue, champParam, roleParam),
    [gameName, tagLine, queue, champParam, roleParam, refreshKey],
  );
  const carry = useAsync(
    () => getCarry(gameName, tagLine, queue, champParam, roleParam),
    [gameName, tagLine, queue, champParam, roleParam, refreshKey],
  );
  const gameLen = useAsync(
    () => getGameLength(gameName, tagLine, queue, champParam, roleParam),
    [gameName, tagLine, queue, champParam, roleParam, refreshKey],
  );

  const scopeLabel = `${champ ? (nameMap.get(champ) ?? champ) : 'All champions'} · ${effectiveRole ? positionLabel(effectiveRole) : 'All roles'}`;

  return (
    <div className="stack">
      {/* Shared filter bar — governs every panel on this tab. */}
      <div className="panel" style={{ padding: '12px 14px' }}>
        <div className="spread" style={{ gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <span className="section-title" style={{ margin: 0 }}>Filter</span>
            <select
              value={champ}
              onChange={(e) => setChamp(e.target.value)}
              style={selectStyle}
              aria-label="Filter by champion"
            >
              <option value="">All champions</option>
              {champOptions.map((c) => (
                <option key={c.id} value={c.id}>{c.label} ({c.games})</option>
              ))}
            </select>
            {roleOptions.length > 1 && (
              <Segmented options={roleOptions} value={effectiveRole} onChange={setRole} teal={false} />
            )}
          </div>
          <span className="note tnum">{scopeLabel}</span>
        </div>
      </div>

      <PerformancePanel perf={perf} champ={champ} role={effectiveRole} />

      <WinFactorsPanel perf={perf} champ={champ} role={effectiveRole} />

      <CarryPanel carry={carry} />

      <GameLengthPanel gameLen={gameLen} />

      <Panel title="Roles & positions" sub={roles.data ? `${roles.data.totalGames} games` : undefined}>
        {roles.loading && !roles.data && <Loading />}
        {roles.error && <ErrorBox message={roles.error} />}
        {roles.data && roles.data.roles.length === 0 && <EmptyState emoji="🧭" message="No role data for this filter" />}
        {roles.data && roles.data.roles.length > 0 && (
          <div className="grid cols-3">
            {roles.data.roles.map((r) => (
              <div key={r.position} className="tile" style={{ padding: 14 }}>
                <div className="row between">
                  <div className="row" style={{ gap: 8 }}>
                    <span style={{ fontSize: 18 }}>{positionIcon(r.position)}</span>
                    <b>{positionLabel(r.position)}</b>
                  </div>
                  <span className={`wr-strong ${wrClass(r.winRate)}`} style={{ fontWeight: 800 }}>{round(r.winRate)}%</span>
                </div>
                <div style={{ margin: '10px 0 8px' }}>
                  <WinRateBar wins={r.wins} losses={r.losses} showText={false} />
                </div>
                <div className="note tnum">{r.games} games · {round(r.avgKda, 2)} KDA · {round(r.avgCsPerMin, 1)} cs/m</div>
              </div>
            ))}
          </div>
        )}
      </Panel>

      <Panel title="When do you play best?" sub="Win rate coloured green above 50%, red below. Bars with no games are grey.">
        {time.loading && !time.data && <Loading />}
        {time.error && <ErrorBox message={time.error} />}
        {time.data && time.data.totalGames === 0 && <EmptyState emoji="⏰" message="No timing data for this filter" />}
        {time.data && time.data.totalGames > 0 && (
          <div className="stack" style={{ gap: 22 }}>
            <div>
              <div className="section-title">By hour of day (your local time)</div>
              <TimeChart buckets={time.data.byHour} />
            </div>
            <div className="dash">
              <div>
                <div className="section-title">By weekday</div>
                <TimeChart buckets={time.data.byWeekday} height={160} />
              </div>
              <div>
                <div className="section-title">By game number in a session (tilt check)</div>
                <TimeChart buckets={time.data.bySession} height={160} />
              </div>
            </div>
          </div>
        )}
      </Panel>
    </div>
  );
}
