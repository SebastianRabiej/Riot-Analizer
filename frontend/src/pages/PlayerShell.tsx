import { useCallback, useMemo, useState } from 'react';
import { NavLink, Outlet, useParams } from 'react-router-dom';
import { getPlayer, refresh as apiRefresh, trackPlayer, untrackPlayer } from '../api';
import { useAsync } from '../hooks';
import { Loading, ErrorBox, Segmented } from '../components/ui';
import { PlayerHeader } from '../components/PlayerHeader';
import { PlayerContext } from '../playerContext';

const QUEUE_OPTIONS = [
  { value: 0, label: 'All' },
  { value: 420, label: 'Solo/Duo' },
  { value: 440, label: 'Flex' },
  { value: 400, label: 'Normals' },
];

const TABS = [
  { to: '.', end: true, icon: '📊', label: 'Overview' },
  { to: 'champions', end: false, icon: '🏆', label: 'Champions' },
  { to: 'players', end: false, icon: '🤝', label: 'Players' },
  { to: 'trends', end: false, icon: '📈', label: 'Trends' },
  { to: 'matches', end: false, icon: '📜', label: 'Matches' },
  { to: 'live', end: false, icon: '🔴', label: 'Live' },
];

export function PlayerShell() {
  const params = useParams();
  const gameName = params.gameName ?? '';
  const tagLine = params.tagLine ?? '';

  const [queueSel, setQueueSel] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [tracking, setTracking] = useState(false);

  const { data: player, loading, error } = useAsync(
    () => getPlayer(gameName, tagLine),
    [gameName, tagLine, refreshKey],
  );

  const doRefresh = useCallback(() => {
    setRefreshing(true);
    apiRefresh(gameName, tagLine)
      .catch(() => undefined)
      .finally(() => {
        setRefreshing(false);
        setRefreshKey((k) => k + 1);
      });
  }, [gameName, tagLine]);

  const doToggleTracked = useCallback(
    (next: boolean) => {
      setTracking(true);
      (next ? trackPlayer(gameName, tagLine) : untrackPlayer(gameName, tagLine))
        .catch(() => undefined)
        .finally(() => {
          setTracking(false);
          setRefreshKey((k) => k + 1);
        });
    },
    [gameName, tagLine],
  );

  const queue = queueSel === 0 ? undefined : queueSel;

  const ctx = useMemo(
    () =>
      player
        ? {
            player,
            gameName,
            tagLine,
            queue,
            setQueue: (q: number | undefined) => setQueueSel(q ?? 0),
            refreshKey,
            refreshing,
            doRefresh,
          }
        : null,
    [player, gameName, tagLine, queue, refreshKey, refreshing, doRefresh],
  );

  if (loading && !player) return <Loading label={`Loading ${gameName}#${tagLine}…`} />;
  if (error && !player) return <ErrorBox message={error} />;
  if (!player || !ctx) return <ErrorBox message="Player not found." />;

  return (
    <PlayerContext.Provider value={ctx}>
      <PlayerHeader
        player={player}
        refreshing={refreshing}
        onRefresh={doRefresh}
        tracking={tracking}
        onToggleTracked={doToggleTracked}
      />

      <div className="spread" style={{ marginTop: 14 }}>
        <nav className="tabs" style={{ border: 'none', margin: 0, flex: 1 }}>
          {TABS.map((t) => (
            <NavLink
              key={t.label}
              to={t.to}
              end={t.end}
              className={({ isActive }) => `tab ${isActive ? 'active' : ''}`}
            >
              <span className="tab-ico">{t.icon}</span>
              {t.label}
            </NavLink>
          ))}
        </nav>
        <Segmented
          options={QUEUE_OPTIONS}
          value={queueSel}
          onChange={setQueueSel}
        />
      </div>

      <div style={{ borderTop: '1px solid var(--border)', marginTop: -1, paddingTop: 18 }}>
        <Outlet />
      </div>
    </PlayerContext.Provider>
  );
}
