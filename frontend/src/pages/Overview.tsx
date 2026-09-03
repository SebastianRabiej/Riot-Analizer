import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getStats, getMatches } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, StatTile, WinRateRing, FormPips, MiniWr } from '../components/ui';
import { ChampionIcon } from '../components/icons';
import { MatchRow } from '../components/MatchRow';
import { InsightsPanel } from '../components/Insights';
import { QueueAdvicePanel } from '../components/QueueAdvice';
import { formatDuration, formatKda, formatNumber, parseRiotId, round } from '../util';

function RecognizeCard() {
  const { gameName, tagLine } = usePlayerCtx();
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const [err, setErr] = useState(false);
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const p = parseRiotId(input);
    if (!p) { setErr(true); return; }
    navigate(`${base}/players/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`);
  };

  return (
    <Panel title="Do I know this player?" sub="Shared history with anyone">
      <p className="note" style={{ marginBottom: 12 }}>
        Paste a Riot ID from champ select or a lobby to see every game you've played with or against them.
      </p>
      <form className={`search block ${err ? 'error' : ''}`} onSubmit={submit}>
        <input
          placeholder="gameName#tagLine"
          value={input}
          onChange={(e) => { setInput(e.target.value); if (err) setErr(false); }}
          style={{ width: '100%' }}
        />
        <button type="submit">Check</button>
      </form>
    </Panel>
  );
}

export function Overview() {
  const { gameName, tagLine, queue, refreshKey } = usePlayerCtx();
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const stats = useAsync(() => getStats(gameName, tagLine, queue), [gameName, tagLine, queue, refreshKey]);
  const recent = useAsync(() => getMatches(gameName, tagLine, 0, 5, queue), [gameName, tagLine, queue, refreshKey]);

  if (stats.loading && !stats.data) return <Loading label="Loading season stats…" />;
  if (stats.error && !stats.data) return <ErrorBox message={stats.error} />;
  const s = stats.data;
  if (!s) return <ErrorBox message="No stats available." />;

  if (s.gamesPlayed === 0) {
    return (
      <EmptyState
        emoji="🕹️"
        message="No matches counted this season yet"
        hint="The backend fetches matches on a schedule — hit Refresh, or check back once games have been pulled. Only current-season games are counted."
      />
    );
  }

  const topChamps = s.championStats.slice(0, 5);

  return (
    <div className="stack">
      <Panel>
        <div className="row wrap" style={{ gap: 22, alignItems: 'center' }}>
          <WinRateRing winRate={s.winRate} />
          <div style={{ flex: 1, minWidth: 260 }}>
            <div className="tiles">
              <StatTile label="Games" value={s.gamesPlayed} sub={`${s.wins}W · ${s.losses}L`} />
              <StatTile label="KDA" value={round(s.avgKda, 2)} sub={formatKda(round(s.avgKills, 1), round(s.avgDeaths, 1), round(s.avgAssists, 1))} />
              <StatTile label="CS / min" value={round(s.avgCsPerMin, 1)} />
              <StatTile label="Vision" value={round(s.avgVisionScore)} sub="avg score" />
              <StatTile label="Damage" value={formatNumber(s.avgDamageToChampions)} sub="to champions" />
              <StatTile label="Avg game" value={formatDuration(Math.round(s.avgGameDurationSec))} />
            </div>
            <div className="row wrap" style={{ marginTop: 14, gap: 10 }}>
              <span className="section-title" style={{ margin: 0 }}>Recent form</span>
              <FormPips form={s.recentForm} />
            </div>
          </div>
        </div>
      </Panel>

      <QueueAdvicePanel gameName={gameName} tagLine={tagLine} queue={queue} refreshKey={refreshKey} />

      <InsightsPanel gameName={gameName} tagLine={tagLine} queue={queue} refreshKey={refreshKey} />

      <div className="dash">
        <Panel
          title="Recent matches"
          action={<Link className="panel-link" to={`${base}/matches`}>See all →</Link>}
        >
          {recent.loading && <Loading label="Loading matches…" />}
          {recent.error && <ErrorBox message={recent.error} />}
          {recent.data && recent.data.content.length === 0 && <EmptyState message="No recent matches." />}
          {recent.data && recent.data.content.length > 0 && (
            <div className="stack" style={{ gap: 8 }}>
              {recent.data.content.map((m) => <MatchRow key={m.matchId} m={m} />)}
            </div>
          )}
        </Panel>

        <div className="stack">
          <RecognizeCard />
          <Panel
            title="Top champions"
            action={<Link className="panel-link" to={`${base}/champions`}>See all →</Link>}
          >
            <div className="stack" style={{ gap: 10 }}>
              {topChamps.map((c) => (
                <Link
                  key={c.championId}
                  to={`${base}/champions/${encodeURIComponent(c.championName)}`}
                  className="row"
                  style={{ gap: 11 }}
                >
                  <ChampionIcon championName={c.championName} size={36} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600 }}>{c.championName}</div>
                    <div className="note tnum">{c.games} games · {round(c.avgKda, 2)} KDA</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className={`wr-strong ${c.winRate >= 53 ? 'good' : c.winRate < 48 ? 'bad' : 'mid'}`} style={{ fontWeight: 800 }}>
                      {round(c.winRate)}%
                    </div>
                    <MiniWr winRate={c.winRate} />
                  </div>
                </Link>
              ))}
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
