import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getHeadToHead } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, StatTile } from '../components/ui';
import { ChampionIcon } from '../components/icons';
import { dateShort, formatKda, round } from '../util';

export function HeadToHead() {
  const { gameName, tagLine, refreshKey } = usePlayerCtx();
  const params = useParams();
  const navigate = useNavigate();
  const oppGameName = params.oppGameName ?? '';
  const oppTagLine = params.oppTagLine ?? '';
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const { data, loading, error } = useAsync(
    () => getHeadToHead(gameName, tagLine, oppGameName, oppTagLine),
    [gameName, tagLine, oppGameName, oppTagLine, refreshKey],
  );

  const derived = useMemo(() => {
    if (!data) return null;
    const withGames = data.matches.filter((m) => m.sameTeam);
    const withWins = withGames.filter((m) => m.playerWin).length;
    return { withGames: withGames.length, withWins };
  }, [data]);

  return (
    <div className="stack">
      <div className="row" style={{ gap: 12 }}>
        <Link to={`${base}/players`} className="panel-link">← Players</Link>
      </div>

      {loading && !data && <Loading label={`Looking up ${oppGameName}#${oppTagLine}…`} />}
      {error && <ErrorBox message={error} />}

      {data && data.totalSharedGames === 0 && (
        <EmptyState
          emoji="🤷"
          message={`You've never crossed paths with ${oppGameName}#${oppTagLine}`}
          hint="No shared games found in the current season's stored matches. If they aren't tracked, only games you both appeared in and that have been fetched will count."
        />
      )}

      {data && data.totalSharedGames > 0 && (
        <>
          <Panel>
            <div className="spread" style={{ marginBottom: 16 }}>
              <div>
                <div className="section-title" style={{ margin: 0 }}>Shared history</div>
                <h2 style={{ fontSize: 20 }}>
                  {data.player.gameName} <span className="h2h-vs">vs</span> {data.opponent.gameName}
                  <span className="muted" style={{ fontSize: 14, fontWeight: 600 }}> #{data.opponent.tagLine}</span>
                </h2>
              </div>
            </div>

            <div className="tiles" style={{ marginBottom: 18 }}>
              <StatTile label="Games together (any)" value={data.totalSharedGames} />
              <StatTile label="On your team" value={data.sameTeamGames} sub={derived ? `${derived.withWins}W · ${derived.withGames - derived.withWins}L` : undefined} />
              <StatTile label="On enemy team" value={data.opposingGames} />
            </div>

            <div className="dash">
              {data.opposingGames > 0 && (
                <Panel title="When you were enemies" className="pad-lg">
                  <div className="h2h-split">
                    <div className="h2h-side">
                      <div className={`h2h-big ${data.playerWinsWhenOpposing >= data.opponentWinsWhenOpposing ? 'win' : 'loss'}`}>{data.playerWinsWhenOpposing}</div>
                      <div className="note">{data.player.gameName}</div>
                    </div>
                    <div className="h2h-vs">—</div>
                    <div className="h2h-side">
                      <div className={`h2h-big ${data.opponentWinsWhenOpposing > data.playerWinsWhenOpposing ? 'win' : 'loss'}`}>{data.opponentWinsWhenOpposing}</div>
                      <div className="note">{data.opponent.gameName}</div>
                    </div>
                  </div>
                </Panel>
              )}
              {data.sameTeamGames > 0 && derived && (
                <Panel title="When you teamed up" className="pad-lg">
                  <div className="row" style={{ gap: 16, alignItems: 'center', justifyContent: 'center', padding: '6px 0' }}>
                    <div style={{ textAlign: 'center' }}>
                      <div className={`h2h-big ${derived.withWins >= derived.withGames - derived.withWins ? 'win' : 'loss'}`}>
                        {round((derived.withWins / Math.max(1, derived.withGames)) * 100)}%
                      </div>
                      <div className="note">win rate as duo · {derived.withWins}W {derived.withGames - derived.withWins}L</div>
                    </div>
                  </div>
                </Panel>
              )}
            </div>
          </Panel>

          <Panel title="Every shared game">
            <div className="table-scroll">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Result</th><th>Side</th><th>Your champ</th><th>Their champ</th>
                    <th className="num">Your K/D/A</th><th className="num">Their K/D/A</th><th>Queue</th><th className="num">When</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.matches.map((m) => (
                    <tr key={m.matchId} className="clickable" onClick={() => navigate(`${base}/matches/${encodeURIComponent(m.matchId)}`)}>
                      <td><span className={`wr-strong ${m.playerWin ? 'good' : 'bad'}`} style={{ fontWeight: 800 }}>{m.playerWin ? 'WIN' : 'LOSS'}</span></td>
                      <td><span className={`relation-badge ${m.sameTeam ? 'with' : 'vs'}`}>{m.sameTeam ? '🤝 With' : '⚔️ Vs'}</span></td>
                      <td><span className="champ-cell"><ChampionIcon championName={m.playerChampionName} size={26} /><span className="cn">{m.playerChampionName}</span></span></td>
                      <td><span className="champ-cell"><ChampionIcon championName={m.opponentChampionName} size={26} /><span className="cn">{m.opponentChampionName}</span></span></td>
                      <td className="num tnum">{formatKda(m.playerKills, m.playerDeaths, m.playerAssists)}</td>
                      <td className="num tnum muted">{formatKda(m.opponentKills, m.opponentDeaths, m.opponentAssists)}</td>
                      <td className="muted">{m.queueName}</td>
                      <td className="num muted tnum">{dateShort(m.gameCreation)}</td>
                      <td className="num"><span className="panel-link">View →</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        </>
      )}
    </div>
  );
}
