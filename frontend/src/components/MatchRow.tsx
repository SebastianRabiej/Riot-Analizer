import { Link } from 'react-router-dom';
import type { MatchSummaryDto } from '../types';
import { usePlayerCtx } from '../playerContext';
import { ChampionIcon, ItemsRow, Spells } from './icons';
import { formatDuration, formatKda, kdaRatio, positionIcon, positionLabel, queueName, round, timeAgo, formatNumber } from '../util';

export function MatchRow({ m }: { m: MatchSummaryDto }) {
  const { gameName, tagLine } = usePlayerCtx();
  const to = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}/matches/${encodeURIComponent(m.matchId)}`;
  const ratio = kdaRatio(m.kills, m.deaths, m.assists);
  return (
    <Link to={to} className={`match clickable-match ${m.win ? 'win' : 'loss'}`}>
      <div className="m-champ">
        <ChampionIcon championName={m.championName} size={44} />
        <Spells spell1={m.summonerSpell1} spell2={m.summonerSpell2} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {m.championName}
          </div>
          <div className="note">
            {positionIcon(m.teamPosition)} {positionLabel(m.teamPosition)}
          </div>
        </div>
      </div>

      <div className={`m-outcome ${m.win ? 'win' : 'loss'}`}>{m.win ? 'WIN' : 'LOSS'}</div>

      <div className="m-kda">
        <div className="nums tnum">{formatKda(m.kills, m.deaths, m.assists)}</div>
        <div className="ratio tnum">{round(ratio, 2)} KDA</div>
      </div>

      <div className="m-meta">
        <div>{queueName(m.queueId, m.queueName)}</div>
        <div>{timeAgo(m.gameCreation)} · {formatDuration(m.gameDurationSec)}</div>
      </div>

      <div className="m-stats tnum">
        <span><b>{round(m.csPerMin, 1)}</b> cs/m</span>
        <span><b>{m.visionScore}</b> vis</span>
        <span><b>{formatNumber(m.damageToChampions)}</b> dmg</span>
      </div>

      <div className="m-items">
        <ItemsRow items={m.items} />
      </div>

      <div className="m-open" aria-hidden>›</div>
    </Link>
  );
}
