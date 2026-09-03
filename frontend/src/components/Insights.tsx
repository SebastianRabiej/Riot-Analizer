import { useNavigate } from 'react-router-dom';
import type { InsightDto } from '../types';
import { getInsights } from '../api';
import { useAsync } from '../hooks';
import { Panel, Loading, ErrorBox, EmptyState } from './ui';
import { parseRiotId } from '../util';

function StreakBadge({ streak }: { streak: number }) {
  if (!streak) return null;
  const win = streak > 0;
  return (
    <span className={`streak-badge ${win ? 'win' : 'loss'}`}>
      {win ? '🔥' : '🧊'} {Math.abs(streak)}-game {win ? 'win' : 'loss'} streak
    </span>
  );
}

export function InsightsPanel({
  gameName,
  tagLine,
  queue,
  refreshKey,
}: {
  gameName: string;
  tagLine: string;
  queue: number | undefined;
  refreshKey: number;
}) {
  const navigate = useNavigate();
  const tz = new Date().getTimezoneOffset();
  const { data, loading, error } = useAsync(
    () => getInsights(gameName, tagLine, tz, queue),
    [gameName, tagLine, queue, refreshKey],
  );

  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;

  const linkFor = (h: InsightDto): string | null => {
    switch (h.linkKind) {
      case 'vs-player':
      case 'with-player': {
        if (!h.linkValue) return null;
        const p = parseRiotId(h.linkValue);
        return p ? `${base}/players/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}` : null;
      }
      case 'vs-champion':
        return h.linkValue ? `${base}/champions/${encodeURIComponent(h.linkValue)}` : null;
      case 'trends':
        return `${base}/trends`;
      default:
        return null;
    }
  };

  return (
    <Panel
      title="Insights"
      sub={data ? `${data.gamesAnalyzed} games analysed` : undefined}
      action={data ? <StreakBadge streak={data.currentStreak} /> : undefined}
    >
      {loading && <Loading label="Reading your season…" />}
      {!loading && error && <ErrorBox message={error} />}
      {!loading && !error && data && (
        <>
          {(data.longestWinStreak > 0 || data.longestLossStreak > 0) && (
            <div className="streak-chips" style={{ marginBottom: 12 }}>
              {data.longestWinStreak > 0 && (
                <span className="streak-chip">Best win streak: {data.longestWinStreak}</span>
              )}
              {data.longestLossStreak > 0 && (
                <span className="streak-chip">Worst loss streak: {data.longestLossStreak}</span>
              )}
            </div>
          )}
          {data.highlights.length === 0 ? (
            <EmptyState
              emoji="🧠"
              message="Not enough games for insights yet"
              hint="Insights need a few games per opponent, champion and role. Check back after more matches are fetched."
            />
          ) : (
            <div className="insight-grid">
              {data.highlights.map((h, i) => {
                const link = linkFor(h);
                return (
                  <button
                    key={`${h.kind}-${i}`}
                    type="button"
                    className={`insight ${h.sentiment} ${link ? 'clickable' : ''}`}
                    onClick={link ? () => navigate(link) : undefined}
                    disabled={!link}
                  >
                    <span className="i-ico">{h.icon}</span>
                    <span style={{ minWidth: 0 }}>
                      <span className="i-title" style={{ display: 'block' }}>{h.title}</span>
                      <span className="i-detail" style={{ display: 'block' }}>{h.detail}</span>
                    </span>
                    {link && <span className="i-arrow">→</span>}
                  </button>
                );
              })}
            </div>
          )}
        </>
      )}
    </Panel>
  );
}
