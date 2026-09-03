import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { MatchParticipantDetailDto, MatchTeamDto } from '../types';
import { getMatchDetail } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, Segmented } from '../components/ui';
import { ChampionIcon, Spells, ItemsRow } from '../components/icons';
import { formatDuration, formatKda, formatNumber, positionIcon, positionLabel, queueName, round, timeAgo } from '../util';
import { MatchAnalysis } from '../components/MatchAnalysis';

function kdaColor(kda: number): string {
  if (kda >= 4) return 'var(--gold)';
  if (kda >= 3) return 'var(--win)';
  if (kda < 1.5) return 'var(--loss)';
  return 'var(--text)';
}

function ScoreRow({
  p,
  isMe,
  maxDamage,
}: {
  p: MatchParticipantDetailDto;
  isMe: boolean;
  maxDamage: number;
}) {
  const named = p.gameName && p.tagLine;
  const dmgPct = maxDamage > 0 ? (p.damageToChampions / maxDamage) * 100 : 0;
  return (
    <tr className={isMe ? 'me-row' : ''}>
      <td>
        <div className="row" style={{ gap: 8 }}>
          <ChampionIcon championName={p.championName} size={36} />
          <Spells spell1={p.summonerSpell1} spell2={p.summonerSpell2} />
          <div style={{ minWidth: 0 }}>
            <div className="row" style={{ gap: 6 }}>
              {named && !isMe ? (
                <Link to={`/player/${encodeURIComponent(p.gameName!)}/${encodeURIComponent(p.tagLine!)}`} style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 130 }}>
                  {p.gameName}
                </Link>
              ) : (
                <span style={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 130 }}>
                  {p.gameName ?? 'Unknown'}
                </span>
              )}
              {isMe && <span className="badge teal">You</span>}
            </div>
            <div className="note">{positionIcon(p.teamPosition)} {positionLabel(p.teamPosition)}</div>
          </div>
        </div>
      </td>
      <td className="num">
        <div className="tnum" style={{ fontWeight: 700 }}>{formatKda(p.kills, p.deaths, p.assists)}</div>
        <div className="note tnum" style={{ color: kdaColor(p.kda) }}>{round(p.kda, 2)} KDA</div>
      </td>
      <td className="num tnum">{p.cs}<div className="note">{round(p.csPerMin, 1)}/m</div></td>
      <td className="num tnum muted">{p.visionScore}</td>
      <td className="num" style={{ minWidth: 96 }}>
        <div className="tnum" style={{ fontWeight: 600 }}>{formatNumber(p.damageToChampions)}</div>
        <div className="mini-bar" style={{ width: '100%', marginTop: 3 }}>
          <span style={{ width: `${dmgPct}%`, background: 'var(--loss)' }} />
        </div>
      </td>
      <td className="num tnum">{formatNumber(p.goldEarned)}</td>
      <td><ItemsRow items={p.items} /></td>
    </tr>
  );
}

function TeamBlock({
  team,
  mePuuid,
  maxDamage,
}: {
  team: MatchTeamDto;
  mePuuid: string;
  maxDamage: number;
}) {
  const blue = team.teamId === 100;
  return (
    <div className={`team ${blue ? 'blue' : 'red'}`}>
      <div className="team-head spread" style={{ color: blue ? 'var(--blue)' : 'var(--loss)' }}>
        <span>{blue ? 'Blue team' : 'Red team'} · <span className={`wr-strong ${team.win ? 'good' : 'bad'}`}>{team.win ? 'Victory' : 'Defeat'}</span></span>
        <span className="note tnum" style={{ color: 'var(--text-3)' }}>{team.kills} kills · {formatNumber(team.goldEarned)} gold</span>
      </div>
      <div className="table-scroll">
        <table className="tbl score-tbl">
          <thead>
            <tr>
              <th>Player</th><th className="num">KDA</th><th className="num">CS</th><th className="num">Vis</th><th className="num">Damage</th><th className="num">Gold</th><th>Items</th>
            </tr>
          </thead>
          <tbody>
            {team.participants.map((p) => (
              <ScoreRow key={p.puuid} p={p} isMe={p.puuid === mePuuid} maxDamage={maxDamage} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function MatchDetail() {
  const { gameName, tagLine, player, refreshKey } = usePlayerCtx();
  const params = useParams();
  const matchId = params.matchId ?? '';
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;
  const [tab, setTab] = useState<'board' | 'analysis'>('board');

  const { data, loading, error } = useAsync(() => getMatchDetail(matchId), [matchId, refreshKey]);

  const allParts = data ? data.teams.flatMap((t) => t.participants) : [];
  const me = allParts.find((p) => p.puuid === player.puuid);
  const maxDamage = allParts.reduce((mx, p) => Math.max(mx, p.damageToChampions), 0);

  return (
    <div className="stack">
      <div className="row" style={{ gap: 12 }}>
        <Link to={`${base}/matches`} className="panel-link">← Match history</Link>
      </div>

      {loading && !data && <Loading label="Loading match…" />}
      {error && <ErrorBox message={error} />}

      {data && (
        <>
          <Panel>
            <div className="spread wrap">
              <div className="row" style={{ gap: 14 }}>
                {me && (
                  <div className={`h2h-big ${me.win ? 'win' : 'loss'}`} style={{ fontSize: 28 }}>
                    {me.win ? 'Victory' : 'Defeat'}
                  </div>
                )}
                <div>
                  <div style={{ fontWeight: 700 }}>{queueName(data.queueId, data.queueName)}</div>
                  <div className="note tnum">
                    {timeAgo(data.gameCreation)} · {formatDuration(data.gameDurationSec)}
                    {data.gameVersion ? ` · patch ${data.gameVersion.split('.').slice(0, 2).join('.')}` : ''}
                  </div>
                </div>
              </div>
              {me && (
                <div className="row" style={{ gap: 10 }}>
                  <ChampionIcon championName={me.championName} size={40} />
                  <div>
                    <div style={{ fontWeight: 700 }}>{me.championName}</div>
                    <div className="note tnum">{formatKda(me.kills, me.deaths, me.assists)} · {round(me.kda, 2)} KDA</div>
                  </div>
                </div>
              )}
            </div>
          </Panel>

          <Segmented
            options={[{ value: 'board', label: '📋 Scoreboard' }, { value: 'analysis', label: '📈 Analysis' }]}
            value={tab}
            onChange={setTab}
          />

          {tab === 'board' ? (
            <div className="stack" style={{ gap: 14 }}>
              {data.teams.map((t) => (
                <TeamBlock key={t.teamId} team={t} mePuuid={player.puuid} maxDamage={maxDamage} />
              ))}
            </div>
          ) : (
            <MatchAnalysis matchId={matchId} mePuuid={player.puuid} />
          )}
        </>
      )}
    </div>
  );
}
