import { Link } from 'react-router-dom';
import type { LiveParticipantDto } from '../types';
import { getLive, getHeadToHead } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState } from '../components/ui';
import { ChampionIcon } from '../components/icons';
import { parseRiotId, queueName, formatDuration } from '../util';

/** Cross-references one live participant against the tracked player's history. */
function LiveRelation({ riotId }: { riotId: string }) {
  const { gameName, tagLine, refreshKey } = usePlayerCtx();
  const parsed = parseRiotId(riotId);
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const { data, loading, error } = useAsync(
    () => (parsed ? getHeadToHead(gameName, tagLine, parsed.gameName, parsed.tagLine) : Promise.reject(new Error('bad id'))),
    [gameName, tagLine, riotId, refreshKey],
  );

  if (!parsed) return null;
  if (loading) return <span className="note">checking…</span>;
  if (error || !data) return <span className="note">—</span>;
  if (data.totalSharedGames === 0) return <span className="note">first time</span>;

  const parts: string[] = [];
  if (data.sameTeamGames > 0) parts.push(`${data.sameTeamGames} with`);
  if (data.opposingGames > 0) parts.push(`${data.opposingGames} vs`);

  return (
    <Link to={`${base}/players/${encodeURIComponent(parsed.gameName)}/${encodeURIComponent(parsed.tagLine)}`} style={{ display: 'inline-block' }}>
      <span className={`relation-badge ${data.sameTeamGames && data.opposingGames ? 'mixed' : data.sameTeamGames ? 'with' : 'vs'}`}>
        🔗 {data.totalSharedGames} shared
      </span>
      <div className="note" style={{ marginTop: 2 }}>{parts.join(' · ')}</div>
    </Link>
  );
}

function Participant({ p, isMe }: { p: LiveParticipantDto; isMe: boolean }) {
  const rank = p.tier ? `${p.tier} ${p.rank ?? ''}`.trim() : null;
  return (
    <div className="live-part">
      <ChampionIcon championName={p.championName} size={34} />
      <div style={{ minWidth: 0 }}>
        <div className="lp-name">
          {p.riotId ?? 'Unknown'}
          {isMe && <span className="badge teal" style={{ marginLeft: 6 }}>You</span>}
        </div>
        {rank && <div className="lp-rank">{rank}{p.leaguePoints != null ? ` · ${p.leaguePoints} LP` : ''}</div>}
      </div>
      <div className="lp-rel">
        {isMe ? <span className="note">—</span> : p.riotId ? <LiveRelation riotId={p.riotId} /> : <span className="note">—</span>}
      </div>
    </div>
  );
}

export function Live() {
  const { gameName, tagLine, player, refreshKey } = usePlayerCtx();
  const { data, loading, error } = useAsync(() => getLive(gameName, tagLine), [gameName, tagLine, refreshKey]);

  if (loading && !data) return <Loading label="Checking for a live game…" />;
  if (error) return <ErrorBox message={error} />;
  if (!data || !data.inGame) {
    return (
      <EmptyState
        emoji="🎮"
        message={`${gameName} is not in a game right now`}
        hint="When a game is live, everyone in the lobby is automatically cross-referenced against your match history — so you can see who you've played with or against before."
      />
    );
  }

  const parts = data.participants ?? [];
  const blue = parts.filter((p) => p.teamId === 100);
  const red = parts.filter((p) => p.teamId === 200);
  const elapsed = data.gameStartTime ? Math.floor((Date.now() - data.gameStartTime) / 1000) : data.gameLengthSec;

  return (
    <div className="stack">
      <Panel>
        <div className="row wrap" style={{ gap: 10 }}>
          <span className="live-dot" />
          <b>Live game</b>
          <span className="dot-sep">•</span>
          <span>{queueName(data.queueId ?? 0, data.gameMode)}</span>
          {elapsed != null && elapsed > 0 && (<><span className="dot-sep">•</span><span className="tnum">{formatDuration(elapsed)} elapsed</span></>)}
          <span className="right note">Rows show your shared history with each player</span>
        </div>
      </Panel>

      <div className="dash">
        <div className="team blue">
          <div className="team-head" style={{ color: 'var(--blue)' }}>Blue team</div>
          {blue.map((p) => <Participant key={p.puuid} p={p} isMe={p.puuid === player.puuid} />)}
        </div>
        <div className="team red">
          <div className="team-head" style={{ color: 'var(--loss)' }}>Red team</div>
          {red.map((p) => <Participant key={p.puuid} p={p} isMe={p.puuid === player.puuid} />)}
        </div>
      </div>
    </div>
  );
}
