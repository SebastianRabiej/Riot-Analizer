import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { getDiscovered, getTracked, trackPlayer, untrackPlayer } from '../api';
import { useAsync } from '../hooks';
import { Panel, Loading, ErrorBox, EmptyState } from '../components/ui';
import { ProfileIcon } from '../components/icons';
import { FetchQueuePanel } from '../components/FetchQueue';
import { parseRiotId, tierEmoji, timeAgo, riotIdString } from '../util';

const PAGE_SIZE = 50;

type Tab = 'tracked' | 'discovered' | 'queue';

function rankLine(p: PlayerDto): string {
  const r = p.soloRank;
  if (r && r.tier) return `${tierEmoji(r.tier)} ${r.tier} ${r.rank} · ${r.leaguePoints} LP`;
  const f = p.flexRank;
  if (f && f.tier) return `${tierEmoji(f.tier)} ${f.tier} ${f.rank} (Flex)`;
  return 'Unranked';
}

/** "in 12m" / "due now" for the scheduler's next look at this player. */
function nextCheckLabel(iso: string | null | undefined): string {
  if (!iso) return 'due now';
  const diffSec = Math.floor((new Date(iso).getTime() - Date.now()) / 1000);
  if (diffSec <= 0) return 'due now';
  if (diffSec < 60) return `in ${diffSec}s`;
  const min = Math.floor(diffSec / 60);
  if (min < 60) return `in ${min}m`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `in ${hr}h`;
  return `in ${Math.floor(hr / 24)}d`;
}

/** Freshness of a player's stored history, as shown under their name. */
function SyncLine({ p, profileOnly }: { p: PlayerDto; profileOnly?: boolean }) {
  const synced = p.backfillComplete === true;
  if (profileOnly) {
    return (
      <div className="tc-meta">
        <span className={`badge ${p.lastFetched ? 'teal' : ''}`}
          title="Discovered players get only profile & rank (refreshed about weekly) — match history is off">
          {p.lastFetched ? 'Profile fetched' : 'Profile pending'}
        </span>
        {' · '}next check {nextCheckLabel(p.nextCheckAt)}
      </div>
    );
  }
  return (
    <div className="tc-meta">
      <span className={`badge ${synced ? 'teal' : ''}`} title={synced
        ? 'Whole current season stored — only new games are fetched now'
        : 'Still pulling their current-season history in batches'}>
        {synced ? 'Season synced' : 'Backfilling'}
      </span>
      {' · '}
      {p.newestMatchAt ? `last game ${timeAgo(new Date(p.newestMatchAt).getTime())}` : 'no games stored yet'}
      {' · '}next check {nextCheckLabel(p.nextCheckAt)}
    </div>
  );
}

function PlayerRow({
  p,
  onOpen,
  actions,
  profileOnly,
}: {
  p: PlayerDto;
  onOpen: (p: PlayerDto) => void;
  actions: React.ReactNode;
  profileOnly?: boolean;
}) {
  const discovered = p.trackingLevel === 'DISCOVERED';
  return (
    <div className="track-card">
      <ProfileIcon iconId={p.profileIconId} />
      <div className="tc-main">
        <div className="tc-name">
          {p.gameName ?? 'Unknown'}<span className="tag"> #{p.tagLine ?? '?'}</span>
          {discovered && p.discoveryDepth != null && (
            <span className="badge" style={{ marginLeft: 8 }}
              title={p.discoveryDepth === 1
                ? 'Played in a match with one of your tracked players'
                : `${p.discoveryDepth} hops away from your tracked players`}>
              {p.discoveryDepth === 1 ? '1 hop' : `${p.discoveryDepth} hops`}
            </span>
          )}
        </div>
        <div className="tc-meta">
          {p.lastFetched
            ? `${rankLine(p)} · lvl ${p.summonerLevel} · profile updated ${timeAgo(new Date(p.lastFetched).getTime())}`
            : 'Profile not fetched yet'}
        </div>
        <SyncLine p={p} profileOnly={profileOnly} />
      </div>
      <div className="tc-actions">
        <button className="btn btn-sm" type="button" onClick={() => onOpen(p)}>Open</button>
        {actions}
      </div>
    </div>
  );
}

export function ManageTracking() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const tab: Tab = tabParam === 'discovered' || tabParam === 'queue' ? tabParam : 'tracked';
  const setTab = (t: Tab) => setParams(t === 'tracked' ? {} : { tab: t }, { replace: true });

  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((k) => k + 1);

  const tracked = useAsync(() => getTracked(), [reloadKey]);

  // Discovered: paged + name filter (debounced).
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    const t = setTimeout(() => { setQuery(search); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [search]);
  const discovered = useAsync(() => getDiscovered(page, PAGE_SIZE, query), [page, query, reloadKey]);

  const [input, setInput] = useState('');
  const [formErr, setFormErr] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [busyPuuid, setBusyPuuid] = useState<string | null>(null);

  const add = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseRiotId(input);
    if (!parsed) {
      setFormErr('Enter a Riot ID as gameName#tagLine.');
      return;
    }
    setFormErr(null);
    setAdding(true);
    trackPlayer(parsed.gameName, parsed.tagLine)
      .then(() => {
        setInput('');
        reload();
      })
      .catch((err) => setFormErr(err instanceof Error ? err.message : 'Could not track that player.'))
      .finally(() => setAdding(false));
  };

  const remove = (p: PlayerDto) => {
    setBusyPuuid(p.puuid);
    untrackPlayer(p.gameName, p.tagLine)
      .catch(() => undefined)
      .finally(() => {
        setBusyPuuid(null);
        reload();
      });
  };

  const promote = (p: PlayerDto) => {
    setBusyPuuid(p.puuid);
    trackPlayer(p.gameName, p.tagLine)
      .catch(() => undefined)
      .finally(() => {
        setBusyPuuid(null);
        reload();
      });
  };

  const open = (p: PlayerDto) =>
    navigate(`/player/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`);

  const d = discovered.data;
  const trackedCount = tracked.data?.length;
  const totalPages = d ? Math.max(1, Math.ceil(d.matching / PAGE_SIZE)) : 1;

  return (
    <div className="stack" style={{ maxWidth: 760, margin: '0 auto' }}>
      <div className="row" style={{ gap: 12 }}>
        <Link to="/" className="panel-link">← Home</Link>
      </div>

      <Panel
        title="Tracked players"
        sub="Tracked players are kept up to date first (profile, ranked, new matches and live games). Players they meet in their games are discovered automatically and filled in with spare API budget."
      >
        <form className={`search block ${formErr ? 'error' : ''}`} onSubmit={add} style={{ maxWidth: 460 }}>
          <input
            placeholder="Add a player  ·  gameName#tagLine"
            value={input}
            onChange={(e) => { setInput(e.target.value); if (formErr) setFormErr(null); }}
            style={{ width: '100%' }}
            disabled={adding}
          />
          <button type="submit" disabled={adding}>{adding ? 'Adding…' : 'Track'}</button>
        </form>
        {adding && <p className="note" style={{ marginTop: 8 }}>Fetching their match history — this can take a moment on first add.</p>}
        {formErr && <p className="note" style={{ marginTop: 8, color: 'var(--loss)' }}>{formErr}</p>}
      </Panel>

      <div className="tabs" role="tablist">
        <button type="button" role="tab" className={`tab ${tab === 'tracked' ? 'active' : ''}`}
          aria-selected={tab === 'tracked'} onClick={() => setTab('tracked')}>
          <span className="tab-ico">⭐</span> Tracked{trackedCount != null ? ` (${trackedCount})` : ''}
        </button>
        <button type="button" role="tab" className={`tab ${tab === 'discovered' ? 'active' : ''}`}
          aria-selected={tab === 'discovered'} onClick={() => setTab('discovered')}>
          <span className="tab-ico">🔭</span> Discovered{d ? ` (${d.total})` : ''}
        </button>
        <button type="button" role="tab" className={`tab ${tab === 'queue' ? 'active' : ''}`}
          aria-selected={tab === 'queue'} onClick={() => setTab('queue')}>
          <span className="tab-ico">⏱</span> Fetch queue
        </button>
      </div>

      {tab === 'tracked' && (
        <Panel title={tracked.data ? `${tracked.data.length} tracked` : 'Loading…'}>
          {tracked.loading && !tracked.data && <Loading label="Loading tracked players…" />}
          {tracked.error && <ErrorBox message={tracked.error} />}
          {tracked.data && tracked.data.length === 0 && (
            <EmptyState emoji="⭐" message="You're not tracking anyone yet" hint="Add a Riot ID above to start pulling their stats and keep them updated." />
          )}
          {tracked.data && tracked.data.length > 0 && (
            <div className="stack track-list" style={{ gap: 10 }}>
              {tracked.data.map((p) => (
                <PlayerRow key={p.puuid} p={p} onOpen={open} actions={
                  <button
                    className="btn btn-sm btn-danger"
                    type="button"
                    onClick={() => remove(p)}
                    disabled={busyPuuid === p.puuid}
                    title={`Stop tracking ${riotIdString(p.gameName, p.tagLine)}`}
                  >
                    {busyPuuid === p.puuid ? 'Removing…' : 'Untrack'}
                  </button>
                } />
              ))}
            </div>
          )}
        </Panel>
      )}

      {tab === 'queue' && <FetchQueuePanel />}

      {tab === 'discovered' && (
        <Panel
          title={d ? `${d.total} discovered` : 'Loading…'}
          sub={d && d.total > 0
            ? d.fetchMatches
              ? `${d.profileFetched} of ${d.total} have profile & rank fetched · ${d.seasonSynced} have their whole season stored. They're fetched only when no tracked player needs anything.`
              : `${d.profileFetched} of ${d.total} have profile & rank fetched (profile only — match history is off). ${d.seasonSynced} have a full season stored from before.`
            : 'Players who appear in your tracked players’ matches show up here.'}
        >
          <div className="search block" style={{ maxWidth: 460, marginBottom: 12 }}>
            <input
              placeholder="Filter by name"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>
          {discovered.loading && !d && <Loading label="Loading discovered players…" />}
          {discovered.error && <ErrorBox message={discovered.error} />}
          {d && d.players.length === 0 && (
            <EmptyState emoji="🔭" message={query ? 'No discovered player matches that name' : 'Nobody discovered yet'}
              hint={query ? undefined : 'Once your tracked players’ matches are stored, everyone they played with or against appears here.'} />
          )}
          {d && d.players.length > 0 && (
            <div className="stack track-list" style={{ gap: 10 }}>
              {d.players.map((p) => (
                <PlayerRow key={p.puuid} p={p} onOpen={open} profileOnly={!d.fetchMatches} actions={
                  <button
                    className="btn btn-sm"
                    type="button"
                    onClick={() => promote(p)}
                    disabled={busyPuuid === p.puuid || !p.gameName || !p.tagLine}
                    title={`Track ${riotIdString(p.gameName, p.tagLine)} — fetched first from now on`}
                  >
                    {busyPuuid === p.puuid ? 'Tracking…' : 'Track'}
                  </button>
                } />
              ))}
            </div>
          )}
          {d && totalPages > 1 && (
            <div className="row" style={{ justifyContent: 'center', marginTop: 14 }}>
              <button className="btn btn-sm" type="button" disabled={page === 0 || discovered.loading}
                onClick={() => setPage((x) => Math.max(0, x - 1))}>← Prev</button>
              <span className="note tnum">Page {page + 1} of {totalPages}</span>
              <button className="btn btn-sm" type="button" disabled={page + 1 >= totalPages || discovered.loading}
                onClick={() => setPage((x) => x + 1)}>Next →</button>
            </div>
          )}
        </Panel>
      )}
    </div>
  );
}
