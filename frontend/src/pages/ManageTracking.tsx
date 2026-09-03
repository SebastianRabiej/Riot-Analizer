import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { getTracked, trackPlayer, untrackPlayer } from '../api';
import { useAsync } from '../hooks';
import { Panel, Loading, ErrorBox, EmptyState } from '../components/ui';
import { ProfileIcon } from '../components/icons';
import { parseRiotId, tierEmoji, timeAgo, riotIdString } from '../util';

function rankLine(p: PlayerDto): string {
  const r = p.soloRank;
  if (r && r.tier) return `${tierEmoji(r.tier)} ${r.tier} ${r.rank} · ${r.leaguePoints} LP`;
  const f = p.flexRank;
  if (f && f.tier) return `${tierEmoji(f.tier)} ${f.tier} ${f.rank} (Flex)`;
  return 'Unranked';
}

export function ManageTracking() {
  const navigate = useNavigate();
  const [reloadKey, setReloadKey] = useState(0);
  const { data, loading, error } = useAsync(() => getTracked(), [reloadKey]);

  const [input, setInput] = useState('');
  const [formErr, setFormErr] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [busyPuuid, setBusyPuuid] = useState<string | null>(null);

  const reload = () => setReloadKey((k) => k + 1);

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

  const open = (p: PlayerDto) =>
    navigate(`/player/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`);

  return (
    <div className="stack" style={{ maxWidth: 760, margin: '0 auto' }}>
      <div className="row" style={{ gap: 12 }}>
        <Link to="/" className="panel-link">← Home</Link>
      </div>

      <Panel
        title="Tracked players"
        sub="These are the players the app keeps up to date on a schedule (profile, ranked, new matches and live games). Anyone else you view is still searchable, just not auto-refreshed."
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

      <Panel title={data ? `${data.length} tracked` : 'Loading…'}>
        {loading && !data && <Loading label="Loading tracked players…" />}
        {error && <ErrorBox message={error} />}
        {data && data.length === 0 && (
          <EmptyState emoji="⭐" message="You're not tracking anyone yet" hint="Add a Riot ID above to start pulling their stats and keep them updated." />
        )}
        {data && data.length > 0 && (
          <div className="stack" style={{ gap: 10 }}>
            {data.map((p) => (
              <div key={p.puuid} className="track-card">
                <ProfileIcon iconId={p.profileIconId} />
                <div className="tc-main">
                  <div className="tc-name">
                    {p.gameName}<span className="tag"> #{p.tagLine}</span>
                  </div>
                  <div className="tc-meta">
                    {rankLine(p)} · lvl {p.summonerLevel} · updated {p.lastFetched ? timeAgo(new Date(p.lastFetched).getTime()) : 'never'}
                  </div>
                </div>
                <div className="tc-actions">
                  <button className="btn btn-sm" type="button" onClick={() => open(p)}>Open</button>
                  <button
                    className="btn btn-sm btn-danger"
                    type="button"
                    onClick={() => remove(p)}
                    disabled={busyPuuid === p.puuid}
                    title={`Stop tracking ${riotIdString(p.gameName, p.tagLine)}`}
                  >
                    {busyPuuid === p.puuid ? 'Removing…' : 'Untrack'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
