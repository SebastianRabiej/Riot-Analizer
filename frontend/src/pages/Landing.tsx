import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { getTracked } from '../api';
import { useAsync } from '../hooks';
import { Loading, ErrorBox } from '../components/ui';
import { ProfileIcon } from '../components/icons';
import { parseRiotId, tierEmoji, timeAgo } from '../util';

/** Compact one-line ranked summary, preferring Solo/Duo then Flex. */
function rankLine(p: PlayerDto): string {
  const r = p.soloRank;
  if (r && r.tier) return `${tierEmoji(r.tier)} ${r.tier} ${r.rank} · ${r.leaguePoints} LP`;
  const f = p.flexRank;
  if (f && f.tier) return `${tierEmoji(f.tier)} ${f.tier} ${f.rank} (Flex)`;
  return 'Unranked';
}

/**
 * Home / index page. Instead of auto-redirecting into a player, we show the
 * tracked players so you can choose one — plus a search box to look anyone up.
 */
export function Landing() {
  const navigate = useNavigate();
  const { data: tracked, loading, error } = useAsync(() => getTracked(), []);
  const [input, setInput] = useState('');
  const [err, setErr] = useState(false);

  const open = (gameName: string, tagLine: string) =>
    navigate(`/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseRiotId(input);
    if (!parsed) {
      setErr(true);
      return;
    }
    open(parsed.gameName, parsed.tagLine);
  };

  return (
    <div className="home">
      <section className="home-hero">
        <div className="brand-mark">RA</div>
        <h1>Riot Analizer</h1>
        <p>
          Your personal League companion. Track a player and dig into champions, matchups,
          teammates, roles, timing — and check whether you've crossed paths with anyone before.
        </p>
        <form
          className={`search block ${err ? 'error' : ''}`}
          onSubmit={submit}
          style={{ maxWidth: 400, margin: '0 auto' }}
        >
          <input
            placeholder="Look up any Riot ID  ·  e.g.  Faker#KR1"
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              if (err) setErr(false);
            }}
            style={{ width: '100%' }}
            aria-label="Search a Riot ID"
          />
          <button type="submit">Search</button>
        </form>
        {err && (
          <p style={{ color: 'var(--loss)', marginTop: 10, fontSize: 13 }}>
            Enter a Riot ID as gameName#tagLine.
          </p>
        )}
      </section>

      <section className="home-tracked">
        <div className="spread" style={{ marginBottom: 14 }}>
          <h2 className="section-title" style={{ margin: 0 }}>Tracked players</h2>
          <Link to="/tracked" className="panel-link">Manage tracking →</Link>
        </div>

        {loading && !tracked && <Loading label="Loading tracked players…" />}
        {error && <ErrorBox message={error} />}

        {tracked && tracked.length === 0 && (
          <div className="panel empty-tracked">
            <div className="emoji">⭐</div>
            <div>You're not tracking anyone yet</div>
            <div className="note" style={{ maxWidth: 380 }}>
              Add a Riot ID to keep a player's profile, ranked and matches auto-updated — or just
              search above to look anyone up.
            </div>
            <Link to="/tracked" className="btn btn-teal" style={{ marginTop: 4 }}>
              Add a player
            </Link>
          </div>
        )}

        {tracked && tracked.length > 0 && (
          <div className="home-grid">
            {tracked.map((p) => (
              <button
                key={p.puuid}
                type="button"
                className="track-card clickable"
                onClick={() => open(p.gameName, p.tagLine)}
                title={`Open ${p.gameName}#${p.tagLine}`}
              >
                <ProfileIcon iconId={p.profileIconId} />
                <div className="tc-main">
                  <div className="tc-name">
                    {p.gameName}
                    <span className="tag"> #{p.tagLine}</span>
                  </div>
                  <div className="tc-meta">
                    {rankLine(p)} · lvl {p.summonerLevel}
                  </div>
                  <div className="tc-meta">
                    updated {p.lastFetched ? timeAgo(new Date(p.lastFetched).getTime()) : 'never'}
                  </div>
                </div>
                <span className="tc-open" aria-hidden="true">→</span>
              </button>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
