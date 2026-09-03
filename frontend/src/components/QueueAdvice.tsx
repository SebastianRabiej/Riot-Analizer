import type { QueueAdvice, QueueAdviceReason } from '../types';
import { getQueueAdvice } from '../api';
import { useAsync } from '../hooks';
import { Panel, Loading, ErrorBox, WinRateRing } from './ui';
import { round } from '../util';

interface Look {
  color: string;
  emoji: string;
  headline: string;
}

function look(signal: QueueAdvice['signal']): Look {
  switch (signal) {
    case 'GO':
      return { color: 'var(--win)', emoji: '🟢', headline: 'Queue up' };
    case 'STOP':
      return { color: 'var(--loss)', emoji: '🔴', headline: 'Take a break' };
    default:
      return { color: 'var(--gold)', emoji: '🟡', headline: 'One more — then reassess' };
  }
}

function sinceLabel(min: number): string | null {
  if (min < 0) return null;
  if (min < 1) return 'just now';
  if (min < 60) return `${min} min ago`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? `${h}h ${m}m ago` : `${h}h ago`;
}

function ReasonRow({ r }: { r: QueueAdviceReason }) {
  const color = r.tone === 'good' ? 'var(--win)' : r.tone === 'bad' ? 'var(--loss)' : 'var(--text-3)';
  return (
    <div className="row" style={{ gap: 9, alignItems: 'flex-start', padding: '5px 0' }}>
      <span style={{ flex: '0 0 auto', width: 7, height: 7, borderRadius: '50%', background: color, marginTop: 6 }} />
      <span style={{ fontSize: 13.5, lineHeight: 1.4 }}>{r.text}</span>
    </div>
  );
}

export function QueueAdvicePanel({
  gameName,
  tagLine,
  queue,
  refreshKey,
}: {
  gameName: string;
  tagLine: string;
  queue: number | undefined;
  refreshKey: number;
}) {
  const tz = new Date().getTimezoneOffset();
  const { data, loading, error } = useAsync(
    () => getQueueAdvice(gameName, tagLine, tz, queue),
    [gameName, tagLine, queue, refreshKey],
  );

  const lk = data ? look(data.signal) : null;
  const since = data ? sinceLabel(data.minutesSinceLastGame) : null;

  return (
    <Panel title="Should I queue again?" sub="A live read on your next game from your session, timing and streak.">
      {loading && !data && <Loading label="Reading the moment…" />}
      {error && <ErrorBox message={error} />}
      {data && lk && (
        <div className="stack" style={{ gap: 14 }}>
          <div
            className="row wrap"
            style={{
              gap: 16,
              alignItems: 'center',
              padding: '14px 16px',
              borderRadius: 12,
              border: `1px solid ${lk.color}`,
              background: `color-mix(in srgb, ${lk.color} 12%, transparent)`,
            }}
          >
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ fontSize: 22, fontWeight: 800, display: 'flex', gap: 10, alignItems: 'center' }}>
                <span>{lk.emoji}</span>
                <span style={{ color: lk.color }}>{lk.headline}</span>
              </div>
              <div className="note" style={{ marginTop: 4 }}>
                {data.sampleSize > 0
                  ? <>Next game would be <b>game {data.sessionPositionNext}</b> of this session{since ? <> · last game {since}</> : null}</>
                  : 'No games this season yet.'}
              </div>
            </div>
            <WinRateRing
              winRate={data.predictedWinRate}
              size={104}
              stroke={10}
              center={
                <div>
                  <div style={{ fontSize: 23, fontWeight: 800 }}>{round(data.predictedWinRate)}%</div>
                  <div className="note">predicted</div>
                </div>
              }
            />
          </div>

          <div>
            {data.reasons.map((r, i) => <ReasonRow key={i} r={r} />)}
          </div>

          <div className="note" style={{ fontSize: 11 }}>
            An estimate from your own history{data.sampleSize > 0 ? ` (${data.sampleSize} games)` : ''}, not a guarantee — smaller samples are shrunk toward your season average.
          </div>
        </div>
      )}
    </Panel>
  );
}
