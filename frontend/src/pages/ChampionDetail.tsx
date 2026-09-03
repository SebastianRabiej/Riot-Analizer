import { Link, useNavigate, useParams } from 'react-router-dom';
import { getStats, getVsChampion, getLaneOpponents } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, StatTile, WinRateRing, FormPips } from '../components/ui';
import { ChampionIcon } from '../components/icons';
import { dateShort, formatKda, queueName, round } from '../util';

export function ChampionDetail() {
  const { gameName, tagLine, queue, refreshKey } = usePlayerCtx();
  const params = useParams();
  const navigate = useNavigate();
  const championName = params.championName ?? '';
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const stats = useAsync(() => getStats(gameName, tagLine, queue), [gameName, tagLine, queue, refreshKey]);
  const vs = useAsync(() => getVsChampion(gameName, tagLine, championName), [gameName, tagLine, championName, refreshKey]);
  const lane = useAsync(() => getLaneOpponents(gameName, tagLine), [gameName, tagLine, refreshKey]);

  const played = stats.data?.championStats.find(
    (c) => c.championName.toLowerCase() === championName.toLowerCase(),
  );
  const laneRow = lane.data?.find((c) => c.championName.toLowerCase() === championName.toLowerCase());

  return (
    <div className="stack">
      <div className="row" style={{ gap: 12 }}>
        <Link to={`${base}/champions`} className="panel-link">← Champions</Link>
        <div className="row" style={{ gap: 12, marginLeft: 6 }}>
          <ChampionIcon championName={championName} size={46} />
          <h2 style={{ fontSize: 22 }}>{championName}</h2>
        </div>
      </div>

      <div className="dash">
        {/* Playing this champion */}
        <Panel title={`When you play ${championName}`}>
          {stats.loading && !stats.data && <Loading />}
          {stats.error && <ErrorBox message={stats.error} />}
          {stats.data && !played && (
            <EmptyState emoji="🙈" message={`You haven't played ${championName} this season`} hint="Pick them in a game and their stats will appear here." />
          )}
          {played && (
            <div className="row wrap" style={{ gap: 20, alignItems: 'center' }}>
              <WinRateRing winRate={played.winRate} size={100} stroke={10} />
              <div className="tiles" style={{ flex: 1, minWidth: 220 }}>
                <StatTile label="Games" value={played.games} sub={`${played.wins}W · ${played.losses}L`} />
                <StatTile label="KDA" value={round(played.avgKda, 2)} sub={formatKda(round(played.avgKills, 1), round(played.avgDeaths, 1), round(played.avgAssists, 1))} />
                <StatTile label="CS / min" value={round(played.avgCsPerMin, 1)} />
              </div>
            </div>
          )}
        </Panel>

        {/* Lane matchup summary */}
        <Panel title={`Facing ${championName} in your lane`}>
          {lane.loading && !lane.data && <Loading />}
          {lane.error && <ErrorBox message={lane.error} />}
          {lane.data && !laneRow && (
            <EmptyState emoji="🛣️" message="No direct lane matchups" hint={`You haven't faced ${championName} in your own position this season.`} />
          )}
          {laneRow && (
            <div className="row wrap" style={{ gap: 20, alignItems: 'center' }}>
              <WinRateRing winRate={laneRow.winRate} size={100} stroke={10} />
              <div className="tiles" style={{ flex: 1, minWidth: 180 }}>
                <StatTile label="Lane games" value={laneRow.games} sub={`${laneRow.wins}W · ${laneRow.losses}L`} />
                <StatTile label="Your KDA" value={round(laneRow.avgKda, 2)} />
              </div>
            </div>
          )}
        </Panel>
      </div>

      {/* Anywhere on the map: vs this champion */}
      <Panel title={`Every game against ${championName}`} sub="Enemy on either team, any position">
        {vs.loading && !vs.data && <Loading />}
        {vs.error && <ErrorBox message={vs.error} />}
        {vs.data && vs.data.gamesAgainst === 0 && (
          <EmptyState emoji="👻" message={`You haven't faced ${championName} this season`} />
        )}
        {vs.data && vs.data.gamesAgainst > 0 && (
          <>
            <div className="row wrap" style={{ gap: 22, alignItems: 'center', marginBottom: 16 }}>
              <WinRateRing winRate={vs.data.winRate} size={104} stroke={10} />
              <div className="tiles" style={{ flex: 1, minWidth: 240 }}>
                <StatTile label="Games vs" value={vs.data.gamesAgainst} sub={`${vs.data.wins}W · ${vs.data.losses}L`} tone={vs.data.wins >= vs.data.losses ? 'win' : 'loss'} />
                <StatTile label="Your KDA" value={round(vs.data.avgKda, 2)} sub={formatKda(round(vs.data.avgKills, 1), round(vs.data.avgDeaths, 1), round(vs.data.avgAssists, 1))} />
              </div>
              <div>
                <div className="section-title">Form vs {championName}</div>
                <FormPips form={vs.data.matches.map((m) => m.win)} max={16} />
              </div>
            </div>
            <div className="table-scroll">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Result</th><th>Your champion</th><th>Queue</th><th className="num">K / D / A</th><th className="num">When</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {vs.data.matches.map((m) => (
                    <tr key={m.matchId} className="clickable" onClick={() => navigate(`${base}/matches/${encodeURIComponent(m.matchId)}`)}>
                      <td><span className={`wr-strong ${m.win ? 'good' : 'bad'}`} style={{ fontWeight: 800 }}>{m.win ? 'WIN' : 'LOSS'}</span></td>
                      <td>
                        <span className="champ-cell"><ChampionIcon championName={m.playerChampionName} size={28} /><span className="cn">{m.playerChampionName}</span></span>
                      </td>
                      <td className="muted">{queueName(0, m.queueName)}</td>
                      <td className="num tnum">{formatKda(m.kills, m.deaths, m.assists)}</td>
                      <td className="num muted tnum">{dateShort(m.gameCreation)}</td>
                      <td className="num"><span className="panel-link">View →</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Panel>
    </div>
  );
}
