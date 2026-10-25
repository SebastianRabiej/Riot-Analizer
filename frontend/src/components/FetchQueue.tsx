import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { EarlyChecks, FetchQueueDto, FetchQueueEntry } from '../types';
import { getFetchQueue } from '../api';
import { Panel, Loading, ErrorBox, EmptyState } from './ui';
import { ProfileIcon } from './icons';
import { ApiBudgetCard, budgetWait } from './ApiBudget';

const POLL_MS = 3000;

/** Poll the scheduler snapshot; keeps the last good data while refreshing. */
function useFetchQueue() {
  const [data, setData] = useState<FetchQueueDto | null>(null);
  const [receivedAt, setReceivedAt] = useState(Date.now());
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    const load = () =>
      getFetchQueue(15)
        .then((d) => { if (active) { setData(d); setReceivedAt(Date.now()); setError(null); } })
        .catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Could not load the queue'); });
    load();
    const id = setInterval(load, POLL_MS);
    return () => { active = false; clearInterval(id); };
  }, []);
  return { data, receivedAt, error };
}

/** Re-render every second so relative times stay live between polls. */
function useNow(): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

function span(sec: number): string {
  if (sec < 60) return `${sec}s`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m`;
  const hr = Math.floor(min / 60);
  if (hr < 48) return `${hr}h ${min % 60 ? `${min % 60}m` : ''}`.trim();
  return `${Math.floor(hr / 24)}d`;
}

/** "never checked" / "overdue 3m" / "due now" / "in 12m". */
function dueLabel(iso: string | null, now: number): { text: string; cls: string } {
  if (!iso) return { text: 'never checked', cls: 'fq-due new' };
  const diff = Math.round((new Date(iso).getTime() - now) / 1000);
  if (diff <= -60) return { text: `overdue ${span(-diff)}`, cls: 'fq-due late' };
  if (diff <= 0) return { text: 'due now', cls: 'fq-due' };
  return { text: `in ${span(diff)}`, cls: 'fq-due later' };
}

/** Hint for a not-yet-due player: when spare budget may pull them forward. */
function earlyHint(e: FetchQueueEntry, ec: EarlyChecks, now: number): string | null {
  if (!ec.enabled) return null;
  const open = e.trackingLevel === 'TRACKED' ? ec.trackedOpen : ec.discoveredOpen;
  const at = e.earlyCheckAt ? new Date(e.earlyCheckAt).getTime() : null;
  if (e.nextCheckAt && at !== null && at >= new Date(e.nextCheckAt).getTime()) return null; // backing off
  if (at === null || at <= now) return open ? 'can go early now' : 'can go early when budget frees up';
  return `can go early in ${span(Math.round((at - now) / 1000))}`;
}

function EarlyBadge() {
  return (
    <span className="badge teal" title="Not due yet — checked now because the Riot API budget has spare room">
      Early
    </span>
  );
}

/** One line on whether spare budget is being used to check players early. */
function earlySummary(q: FetchQueueDto): string {
  const ec = q.earlyChecks;
  if (!ec.enabled) return 'Early checks are off — players are checked only on their regular schedule.';
  const free = Math.max(0, ec.longLimit - ec.longUsed);
  const waiting = q.earlyTracked + q.earlyDiscovered;
  const lanes: string[] = [];
  if (ec.trackedOpen) lanes.push('tracked');
  if (ec.discoveredOpen) lanes.push('discovered');
  const budget = `${free}/${ec.longLimit} free (tracked need ${ec.trackedMinFree}, discovered ${ec.discoveredMinFree})`;
  if (lanes.length === 0) {
    return `Early checks paused — budget too busy: ${budget}.${waiting > 0 ? ` ${waiting} player${waiting === 1 ? '' : 's'} waiting to go early.` : ''}`;
  }
  return `Spare budget — checking ${lanes.join(' and ')} players early: ${budget}. `
    + `Each player at most every ${span(Math.round(ec.trackedMinGapMs / 1000))} (tracked) / `
    + `${span(Math.round(ec.discoveredMinGapMs / 1000))} (discovered).`;
}

function name(p: { gameName: string | null; tagLine: string | null }) {
  return (
    <>
      {p.gameName ?? 'Unknown'}<span className="tag"> #{p.tagLine ?? '?'}</span>
    </>
  );
}

function playerHref(p: { gameName: string | null; tagLine: string | null }): string | null {
  if (!p.gameName || !p.tagLine) return null;
  return `/player/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`;
}

function LevelBadge({ e }: { e: Pick<FetchQueueEntry, 'trackingLevel' | 'discoveryDepth'> }) {
  if (e.trackingLevel === 'TRACKED') return <span className="badge gold">Tracked</span>;
  const d = e.discoveryDepth ?? 1;
  return <span className="badge">{d === 1 ? '1 hop' : `${d} hops`}</span>;
}

function CheckBadge({ type }: { type: FetchQueueEntry['checkType'] }) {
  return type === 'HEAD_CHECK'
    ? <span className="badge teal" title="Season stored — one call asks Riot for games since the newest one we have">Head check</span>
    : <span className="badge violet" title="Season not fully stored — pulls the next batch of missing games">Backfill</span>;
}

function calls(e: FetchQueueEntry): string {
  if (e.minCalls === e.maxCalls) return `${e.minCalls} Riot call${e.minCalls === 1 ? '' : 's'}`;
  // A backfill may scan extra match-list pages, so its upper bound is approximate.
  return `${e.checkType === 'BACKFILL' ? '≈ ' : ''}${e.minCalls}–${e.maxCalls} Riot calls`;
}

function NextRow({ e, now }: { e: FetchQueueEntry; now: number }) {
  const regular = dueLabel(e.nextCheckAt, now);
  const due = e.early ? { text: `regular ${regular.text}`, cls: 'fq-due later' } : regular;
  const href = playerHref(e);
  return (
    <div className={`fq-row ${e.position === 1 ? 'first' : ''}`}>
      <div className="fq-pos tnum">{e.position}</div>
      <div className="fq-icon"><ProfileIcon iconId={e.profileIconId} /></div>
      <div className="fq-main">
        <div className="fq-title">
          <span className="fq-name">{href ? <Link to={href}>{name(e)}</Link> : name(e)}</span>
          <LevelBadge e={e} />
          <CheckBadge type={e.checkType} />
          {e.early && <EarlyBadge />}
          {e.inGame && <span className="badge live">In game</span>}
          <span className={due.cls}>{due.text}</span>
        </div>
        <ol className="fq-steps">
          {e.steps.map((s) => <li key={s}>{s}</li>)}
        </ol>
        <div className="fq-meta">{calls(e)} · {e.reason}</div>
      </div>
    </div>
  );
}

function LaterRow({ e, ec, now }: { e: FetchQueueEntry; ec: EarlyChecks; now: number }) {
  const due = dueLabel(e.nextCheckAt, now);
  const hint = earlyHint(e, ec, now);
  const href = playerHref(e);
  return (
    <div className="fq-later">
      <div className="fq-later-top">
        <span className="fq-name">{href ? <Link to={href}>{name(e)}</Link> : name(e)}</span>
        <span className={due.cls}>{due.text}</span>
      </div>
      <div className="fq-meta">
        {e.trackingLevel === 'DISCOVERED' && <><LevelBadge e={e} />{' '}</>}
        {e.inGame && <><span className="badge live">In game</span>{' '}</>}
        {e.reason}
        {hint && <span className="fq-early"> · {hint}</span>}
      </div>
    </div>
  );
}

function NowCard({ q, now, receivedAt }: { q: FetchQueueDto; now: number; receivedAt: number }) {
  const r = q.running;
  if (r) {
    const since = Math.max(0, Math.round((now - new Date(r.startedAt).getTime()) / 1000));
    const stalledMs = budgetWait(q.rateLimit, r.trackingLevel, receivedAt, now);
    const href = playerHref(r);
    return (
      <div className="fq-now active">
        <span className="fq-pulse" aria-hidden />
        <div>
          <div className="fq-title">
            <span>Fetching <b className="fq-name">{href ? <Link to={href}>{name(r)}</Link> : name(r)}</b></span>
            <LevelBadge e={{ trackingLevel: r.trackingLevel, discoveryDepth: null }} />
            <CheckBadge type={r.checkType} />
            {r.early && <EarlyBadge />}
          </div>
          <div className="fq-meta">
            started {span(since)} ago
            {stalledMs > 0 && (
              <span className="fq-stall"> · waiting for API budget — next call in {Math.ceil(stalledMs / 1000)}s</span>
            )}
          </div>
        </div>
      </div>
    );
  }
  const due = q.dueTracked + q.dueDiscovered;
  let nextRun = '';
  if (q.lastRunAt) {
    const left = Math.round((new Date(q.lastRunAt).getTime() + q.schedulerTickMs - now) / 1000);
    nextRun = left > 0 ? `next scheduler run in ${left}s` : 'scheduler run starting';
  }
  return (
    <div className="fq-now">
      <span className="fq-pulse idle" aria-hidden />
      <div>
        <div className="fq-title">{due > 0 ? 'Idle between runs' : 'Idle — everyone is up to date'}</div>
        <div className="fq-meta">
          {q.lastRunAt ? `last run checked ${q.lastRunChecked} player${q.lastRunChecked === 1 ? '' : 's'}` : 'no run yet since the server started'}
          {nextRun && ` · ${nextRun}`}
        </div>
      </div>
    </div>
  );
}

export function FetchQueuePanel() {
  const { data: q, receivedAt, error } = useFetchQueue();
  const now = useNow();

  if (!q) {
    return error ? <ErrorBox message={error} /> : <Panel><Loading label="Loading the fetch queue…" /></Panel>;
  }

  const dueTotal = q.dueTracked + q.dueDiscovered;
  const earlyInList = q.next.filter((e) => e.early).length;
  const earlyOpenTotal = (q.earlyChecks.trackedOpen ? q.earlyTracked : 0)
    + (q.earlyChecks.discoveredOpen ? q.earlyDiscovered : 0);
  const hidden = Math.max(0, dueTotal + earlyOpenTotal - q.next.length);
  const earlyNote = earlyInList > 0 ? ` ${earlyInList} shown early (spare budget).` : '';

  return (
    <div className="stack">
      {error && <p className="note" style={{ color: 'var(--loss)' }}>Refresh failed ({error}) — showing the last snapshot.</p>}

      <Panel title="Right now" sub="Live view of the background fetcher · refreshes every 3s">
        <NowCard q={q} now={now} receivedAt={receivedAt} />
      </Panel>

      <Panel title="Riot API budget"
        sub="Sliding window: each call frees up exactly one window-length after it was made. Background work stops early to keep room for you.">
        <ApiBudgetCard rl={q.rateLimit} receivedAt={receivedAt} now={now} />
      </Panel>

      <Panel
        title="Up next"
        sub={(dueTotal === 0
          ? (earlyInList > 0 ? 'Nobody is due — spare budget is used to check these players early.' : 'Nobody is due — the queue below shows who comes up next.')
          : `${q.dueTracked} tracked · ${q.dueDiscovered} discovered due now. Tracked players always go first; the order is re-checked after every player.${earlyNote}`)}
      >
        <p className="note fq-early-summary">{earlySummary(q)}</p>
        {q.next.length === 0 ? (
          <EmptyState emoji="✅" message="Nothing waiting" hint="Every player's next check is in the future." />
        ) : (
          <div className="fq-list">
            {q.next.map((e) => <NextRow key={e.puuid} e={e} now={now} />)}
            {hidden > 0 && <p className="note fq-more">+ {hidden} more player{hidden === 1 ? '' : 's'} after these</p>}
          </div>
        )}
      </Panel>

      <Panel title="Later" sub="Regular schedule, soonest first. While the API budget has spare room, players are pulled forward early (tracked before the discovered backlog).">
        <div className="fq-lanes">
          <div>
            <div className="section-title">⭐ Tracked</div>
            {q.upcomingTracked.length === 0
              ? <p className="note">No tracked player scheduled.</p>
              : q.upcomingTracked.map((e) => <LaterRow key={e.puuid} e={e} ec={q.earlyChecks} now={now} />)}
          </div>
          <div>
            <div className="section-title">🔭 Discovered</div>
            {q.upcomingDiscovered.length === 0
              ? <p className="note">No discovered player scheduled.</p>
              : q.upcomingDiscovered.map((e) => <LaterRow key={e.puuid} e={e} ec={q.earlyChecks} now={now} />)}
          </div>
        </div>
      </Panel>

      <p className="note">
        Predicted from stored state. What can't be known in advance: how many new games Riot returns
        (so the call count is a range), and exact timing — your own requests and rate-limit reserves come first.
      </p>
    </div>
  );
}
