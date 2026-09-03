import type { RankDto } from '../types';
import { round, tierEmoji } from '../util';

export function RankBadge({ label, rank }: { label: string; rank: RankDto | null }) {
  if (!rank || !rank.tier) {
    return (
      <div className="rank-badge unranked">
        <div className="rank-emblem">🎯</div>
        <div className="rank-info">
          <div className="rb-queue">{label}</div>
          <div className="rb-tier">Unranked</div>
        </div>
      </div>
    );
  }
  const total = rank.wins + rank.losses;
  const wr =
    rank.winRate != null ? round(rank.winRate) : total > 0 ? round((rank.wins / total) * 100) : 0;
  const tierCls = `tier-${rank.tier.toUpperCase()}`;
  return (
    <div className="rank-badge">
      <div className="rank-emblem">{tierEmoji(rank.tier)}</div>
      <div className="rank-info">
        <div className="rb-queue">{label}</div>
        <div className={`rb-tier ${tierCls}`}>
          {rank.tier} {rank.rank} <span className="rb-lp">· {rank.leaguePoints} LP</span>
        </div>
        <div className="rb-wl tnum">
          {rank.wins}W {rank.losses}L · {wr}%
        </div>
      </div>
    </div>
  );
}
