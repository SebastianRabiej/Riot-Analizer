import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getStats, getChampionMatchups, getLaneOpponents } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, Segmented, MiniWr } from '../components/ui';
import { ChampionIcon } from '../components/icons';
import { round, wrClass } from '../util';

type Mode = 'play' | 'face' | 'lane';
type SortKey = 'games' | 'winRate' | 'kda' | 'cs';

interface Row {
  championId: number | null;
  championName: string;
  games: number;
  wins: number;
  losses: number;
  winRate: number;
  kda: number;
  k: number;
  d: number;
  a: number;
  cs?: number;
}

export function Champions() {
  const { gameName, tagLine, queue, refreshKey } = usePlayerCtx();
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;
  const [mode, setMode] = useState<Mode>('play');
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'games', dir: 'desc' });

  const played = useAsync(() => getStats(gameName, tagLine, queue), [gameName, tagLine, queue, refreshKey]);
  const faced = useAsync(() => getChampionMatchups(gameName, tagLine), [gameName, tagLine, refreshKey]);
  const lane = useAsync(() => getLaneOpponents(gameName, tagLine), [gameName, tagLine, refreshKey]);

  const active = mode === 'play' ? played : mode === 'face' ? faced : lane;

  const rows: Row[] = useMemo(() => {
    if (mode === 'play' && played.data) {
      return played.data.championStats.map((c) => ({
        championId: c.championId, championName: c.championName, games: c.games, wins: c.wins,
        losses: c.losses, winRate: c.winRate, kda: c.avgKda, k: c.avgKills, d: c.avgDeaths, a: c.avgAssists, cs: c.avgCsPerMin,
      }));
    }
    const src = mode === 'face' ? faced.data : lane.data;
    if (src) {
      return src.map((c) => ({
        championId: c.championId, championName: c.championName, games: c.games, wins: c.wins,
        losses: c.losses, winRate: c.winRate, kda: c.avgKda, k: c.avgKills, d: c.avgDeaths, a: c.avgAssists,
      }));
    }
    return [];
  }, [mode, played.data, faced.data, lane.data]);

  const sorted = useMemo(() => {
    const arr = [...rows];
    const mul = sort.dir === 'asc' ? 1 : -1;
    arr.sort((x, y) => {
      const xv = sort.key === 'cs' ? (x.cs ?? 0) : x[sort.key];
      const yv = sort.key === 'cs' ? (y.cs ?? 0) : y[sort.key];
      return (xv - yv) * mul;
    });
    return arr;
  }, [rows, sort]);

  const toggleSort = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'desc' }));

  const caret = (key: SortKey) => (sort.key === key ? <span className="sort-caret">{sort.dir === 'asc' ? '▲' : '▼'}</span> : null);

  const heading =
    mode === 'play' ? 'Champions you play'
    : mode === 'face' ? 'Enemy champions you face (anywhere on the map)'
    : 'Lane opponents (enemy in your own position)';

  return (
    <Panel
      title={heading}
      sub={active.data ? `${sorted.length} champions` : undefined}
      action={
        <Segmented
          options={[{ value: 'play', label: 'I play' }, { value: 'face', label: 'I face' }, { value: 'lane', label: 'Lane vs' }]}
          value={mode}
          onChange={(m) => setMode(m as Mode)}
        />
      }
    >
      {active.loading && !active.data && <Loading label="Loading champions…" />}
      {active.error && <ErrorBox message={active.error} />}
      {active.data && sorted.length === 0 && (
        <EmptyState emoji="🏆" message="No champion data yet" hint="Play a few games (or wait for the fetcher) and they'll show up here." />
      )}
      {sorted.length > 0 && (
        <div className="table-scroll">
          <table className="tbl">
            <thead>
              <tr>
                <th>Champion</th>
                <th className="num sortable" onClick={() => toggleSort('games')}>Games{caret('games')}</th>
                <th className="num sortable" onClick={() => toggleSort('winRate')}>Win rate{caret('winRate')}</th>
                <th className="num sortable" onClick={() => toggleSort('kda')}>KDA{caret('kda')}</th>
                <th className="num">K / D / A</th>
                {mode === 'play' && <th className="num sortable" onClick={() => toggleSort('cs')}>CS/m{caret('cs')}</th>}
              </tr>
            </thead>
            <tbody>
              {sorted.map((r) => (
                <tr key={`${r.championName}-${r.championId}`} className="clickable">
                  <td>
                    <Link to={`${base}/champions/${encodeURIComponent(r.championName)}`} className="champ-cell">
                      <ChampionIcon championName={r.championName} size={34} />
                      <span className="cn">{r.championName}</span>
                    </Link>
                  </td>
                  <td className="num tnum">{r.games}</td>
                  <td className="num">
                    <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
                      <span className={`wr-strong ${wrClass(r.winRate)} tnum`} style={{ fontWeight: 700 }}>{round(r.winRate)}%</span>
                      <MiniWr winRate={r.winRate} />
                    </div>
                    <div className="note tnum" style={{ textAlign: 'right' }}>{r.wins}W {r.losses}L</div>
                  </td>
                  <td className="num tnum">{round(r.kda, 2)}</td>
                  <td className="num tnum muted">{round(r.k, 1)} / {round(r.d, 1)} / {round(r.a, 1)}</td>
                  {mode === 'play' && <td className="num tnum">{round(r.cs ?? 0, 1)}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
