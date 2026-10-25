import type { FetchPriority, RateLimitDto } from '../types';

const LABELS: Record<FetchPriority, { name: string; hint: string }> = {
  REFRESH: { name: 'Your requests', hint: 'Refresh, track, search, live game' },
  ANALYSIS: { name: 'Match analysis', hint: 'Timelines you open' },
  TRACKED_HISTORY: { name: 'Tracked players', hint: 'Background, keeps a reserve free' },
  DISCOVERED_HISTORY: { name: 'Discovered players', hint: 'Background, spare budget only' },
};

/** ms left of a server-relative countdown, given how long ago the snapshot arrived. */
export function remainingMs(ms: number | null, receivedAt: number, now: number): number {
  if (ms == null) return 0;
  return Math.max(0, ms - (now - receivedAt));
}

function secs(ms: number): string {
  const s = Math.ceil(ms / 1000);
  if (s < 60) return `${s}s`;
  return `${Math.floor(s / 60)}m ${String(s % 60).padStart(2, '0')}s`;
}

function windowLabel(ms: number): string {
  const s = Math.round(ms / 1000);
  return s >= 60 ? `${Math.round(s / 60)} min` : `${s} s`;
}

/** Usage bar with tick marks where the background reserves start. */
function UsageBar({ used, limit, marks }: {
  used: number; limit: number; marks: { at: number; label: string; short: string }[];
}) {
  const pct = Math.min(100, (used / limit) * 100);
  const lowest = Math.min(...marks.map((m) => m.at), limit);
  const highest = Math.max(...marks.map((m) => m.at), 0);
  const cls = used >= limit ? 'full' : used >= highest ? 'hot' : used >= lowest ? 'warm' : '';
  return (
    <div className="ab-bar" role="meter" aria-valuemin={0} aria-valuemax={limit} aria-valuenow={used}>
      <div className={`ab-fill ${cls}`} style={{ width: `${pct}%` }} />
      {marks.map((m) => (
        <div key={m.label} className="ab-mark" style={{ left: `${(m.at / limit) * 100}%` }} title={m.label}>
          <span className="ab-mark-label tnum">{m.at}<span className="ab-mark-text"> · {m.short}</span></span>
        </div>
      ))}
    </div>
  );
}

export function ApiBudgetCard({ rl, receivedAt, now }: { rl: RateLimitDto; receivedAt: number; now: number }) {
  const long = rl.longWindow;
  const marks = rl.priorities
    .filter((p) => p.reserve > 0)
    .map((p) => ({ at: p.longLimit, label: `${LABELS[p.priority].name} stop at ${p.longLimit}`,
      short: `${p.priority === 'TRACKED_HISTORY' ? 'tracked' : 'discovered'} stop` }));
  const oldestOut = remainingMs(long.nextExpiryInMs, receivedAt, now);
  const riot = rl.riot;
  const riotLong = riot?.appWindows.find((w) => w.windowSec * 1000 === long.windowMs)
    ?? riot?.appWindows[riot.appWindows.length - 1];
  const riotShort = riot?.appWindows.find((w) => w.windowSec * 1000 === rl.shortWindow.windowMs);

  return (
    <div className="ab">
      <div className="ab-head">
        <div>
          <span className="ab-big tnum">{long.used}</span>
          <span className="ab-of tnum"> / {long.limit}</span>
          <span className="note"> calls in the last {windowLabel(long.windowMs)}</span>
        </div>
        <div className="note tnum">
          {rl.shortWindow.used} / {rl.shortWindow.limit} in the last {windowLabel(rl.shortWindow.windowMs)}
          {long.used > 0 && ` · oldest call drops out in ${secs(oldestOut)}`}
        </div>
      </div>
      <UsageBar used={long.used} limit={long.limit} marks={marks} />

      <div className="ab-prios">
        {rl.priorities.map((p) => {
          const left = remainingMs(p.freeInMs, receivedAt, now);
          const free = left <= 0;
          return (
            <div key={p.priority} className="ab-prio">
              <div className="ab-prio-name" title={LABELS[p.priority].hint}>{LABELS[p.priority].name}</div>
              <div className="note tnum">
                {p.reserve > 0 ? `stops at ${p.longLimit} / ${rl.longWindow.limit}` : `full ${rl.longWindow.limit}`}
              </div>
              <div className={`ab-prio-state tnum ${free ? 'free' : 'wait'}`}>
                {free ? 'free now' : `next call in ${secs(left)}`}
                {p.waiting > 0 && <span className="note"> · {p.waiting} waiting</span>}
              </div>
            </div>
          );
        })}
      </div>

      <div className="ab-riot note tnum">
        {riot && riotLong ? (
          <>
            Riot's count: <b>{riotLong.count} / {riotLong.limit}</b> ({windowLabel(riotLong.windowSec * 1000)})
            {riotShort && <> · {riotShort.count} / {riotShort.limit} ({windowLabel(riotShort.windowSec * 1000)})</>}
            {riot.observedAt && <> · as of {secs(Math.max(1000, now - new Date(riot.observedAt).getTime()))} ago</>}
            {Math.abs(riotLong.count - long.used) > 5 && riotLong.windowSec * 1000 === long.windowMs && (
              <span className="ab-warn"> · differs from our count — another app on this key, or a recent restart?</span>
            )}
          </>
        ) : 'No Riot response since the server started.'}
        {riot && (
          riot.rateLimitedCount === 0
            ? <> · no 429s</>
            : <span className="ab-warn"> · {riot.rateLimitedCount} × 429
              {riot.lastRateLimitedAt && ` (last ${secs(Math.max(1000, now - new Date(riot.lastRateLimitedAt).getTime()))} ago`}
              {riot.lastRateLimitType && `, ${riot.lastRateLimitType}`}
              {riot.lastRetryAfterSec != null && `, retry after ${riot.lastRetryAfterSec}s`}
              {riot.lastRateLimitedAt && ')'}</span>
        )}
      </div>
    </div>
  );
}

/** Why the running check may be stalled: its priority has no budget right now. */
export function budgetWait(rl: RateLimitDto, level: 'TRACKED' | 'DISCOVERED' | 'NONE', receivedAt: number, now: number): number {
  const prio: FetchPriority = level === 'TRACKED' ? 'TRACKED_HISTORY' : 'DISCOVERED_HISTORY';
  const p = rl.priorities.find((x) => x.priority === prio);
  return p ? remainingMs(p.freeInMs, receivedAt, now) : 0;
}
