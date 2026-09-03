import { Link } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { ProfileIcon } from './icons';
import { RankBadge } from './RankBadge';
import { timeAgo } from '../util';

export function PlayerHeader({
  player,
  refreshing,
  onRefresh,
  tracking,
  onToggleTracked,
}: {
  player: PlayerDto;
  refreshing: boolean;
  onRefresh: () => void;
  tracking: boolean;
  onToggleTracked: (next: boolean) => void;
}) {
  const last = player.lastFetched ? timeAgo(new Date(player.lastFetched).getTime()) : 'never';
  const isTracked = player.tracked;
  return (
    <div className="player-hero">
      <div className="profile-icon-wrap">
        <ProfileIcon iconId={player.profileIconId} />
        <span className="profile-level">{player.summonerLevel}</span>
      </div>
      <div className="hero-id">
        <div className="hero-name">
          {player.gameName}
          <span className="tag"> #{player.tagLine}</span>
        </div>
        <div className="hero-meta">
          <span>Updated {last}</span>
          <span className="dot-sep">•</span>
          <button className="panel-link" onClick={onRefresh} disabled={refreshing} type="button">
            {refreshing ? 'Refreshing…' : '↻ Refresh'}
          </button>
          <span className="dot-sep">•</span>
          <Link className="panel-link" to="/tracked">Manage tracking</Link>
        </div>
      </div>
      <div className="hero-actions">
        <button
          type="button"
          className={`btn track-toggle ${isTracked ? 'is-tracked' : 'btn-teal'}`}
          onClick={() => onToggleTracked(!isTracked)}
          disabled={tracking}
          title={isTracked ? 'Stop tracking this player' : 'Track this player (keeps them auto-updated)'}
        >
          {tracking
            ? (isTracked ? 'Removing…' : 'Tracking…')
            : (isTracked ? '★ Tracking' : '☆ Track player')}
        </button>
      </div>
      <div className="hero-ranks">
        <RankBadge label="Solo/Duo" rank={player.soloRank} />
        <RankBadge label="Flex" rank={player.flexRank} />
      </div>
    </div>
  );
}
