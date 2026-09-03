import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getTeammates, getOpponents } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState, Segmented, MiniWr } from '../components/ui';
import { parseRiotId, round, wrClass } from '../util';

type Mode = 'duos' | 'opponents';
type SortKey = 'games' | 'winRate';

export function Players() {
  const { gameName, tagLine, refreshKey } = usePlayerCtx();
  const base = `/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`;
  const navigate = useNavigate();
  const [mode, setMode] = useState<Mode>('duos');
  const [minGames, setMinGames] = useState(2);
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'games', dir: 'desc' });
  const [input, setInput] = useState('');
  const [err, setErr] = useState(false);

  const duos = useAsync(() => getTeammates(gameName, tagLine, minGames, 300), [gameName, tagLine, minGames, refreshKey]);
  const opps = useAsync(() => getOpponents(gameName, tagLine, minGames, 300), [gameName, tagLine, minGames, refreshKey]);
  const active = mode === 'duos' ? duos : opps;

  const sorted = useMemo(() => {
    const arr = [...(active.data ?? [])];
    const mul = sort.dir === 'asc' ? 1 : -1;
    arr.sort((a, b) => (a[sort.key] - b[sort.key]) * mul);
    return arr;
  }, [active.data, sort]);

  const toggleSort = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'desc' }));
  const caret = (key: SortKey) => (sort.key === key ? <span className="sort-caret">{sort.dir === 'asc' ? '▲' : '▼'}</span> : null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const p = parseRiotId(input);
    if (!p) { setErr(true); return; }
    navigate(`${base}/players/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`);
  };

  const relColLabel = mode === 'duos' ? 'Win rate together' : 'Your win rate vs';

  return (
    <div className="stack">
      <Panel title="Shared history with anyone" sub="Have you played with — or against — this player before?">
        <form className={`search block ${err ? 'error' : ''}`} onSubmit={submit} style={{ maxWidth: 460 }}>
          <input
            placeholder="gameName#tagLine"
            value={input}
            onChange={(e) => { setInput(e.target.value); if (err) setErr(false); }}
            style={{ width: '100%' }}
          />
          <button type="submit">Check history</button>
        </form>
        {err && <p style={{ color: 'var(--loss)', marginTop: 8 }} className="note">Enter a Riot ID as gameName#tagLine.</p>}
      </Panel>

      <Panel
        title={mode === 'duos' ? 'Teammates you play with' : 'Opponents you have faced'}
        sub={active.data ? `${sorted.length} players · min ${minGames} games` : undefined}
        action={
          <div className="row" style={{ gap: 10 }}>
            <Segmented options={[{ value: 2, label: '2+' }, { value: 3, label: '3+' }, { value: 5, label: '5+' }]} value={minGames} onChange={setMinGames} teal={false} />
            <Segmented options={[{ value: 'duos', label: '🤝 Duos' }, { value: 'opponents', label: '⚔️ Opponents' }]} value={mode} onChange={(m) => setMode(m as Mode)} />
          </div>
        }
      >
        {active.loading && !active.data && <Loading label="Loading players…" />}
        {active.error && <ErrorBox message={active.error} />}
        {active.data && sorted.length === 0 && (
          <EmptyState
            emoji="🫂"
            message={mode === 'duos' ? 'No recurring teammates yet' : 'No recurring opponents yet'}
            hint={`Players you've shared at least ${minGames} games with show up here. Lower the threshold or wait for more matches.`}
          />
        )}
        {sorted.length > 0 && (
          <div className="table-scroll">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Player</th>
                  <th className="num sortable" onClick={() => toggleSort('games')}>Games{caret('games')}</th>
                  <th className="num sortable" onClick={() => toggleSort('winRate')}>{relColLabel}{caret('winRate')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((r) => {
                  const named = r.gameName && r.tagLine;
                  const to = named ? `${base}/players/${encodeURIComponent(r.gameName!)}/${encodeURIComponent(r.tagLine!)}` : null;
                  return (
                    <tr key={r.puuid} className={to ? 'clickable' : ''}>
                      <td>
                        {to ? (
                          <Link to={to} style={{ fontWeight: 600 }}>
                            {r.gameName}<span className="muted"> #{r.tagLine}</span>
                          </Link>
                        ) : (
                          <span className="muted" title="Riot ID not resolved yet">Unknown player</span>
                        )}
                      </td>
                      <td className="num tnum">{r.games}</td>
                      <td className="num">
                        <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
                          <span className={`wr-strong ${wrClass(r.winRate)} tnum`} style={{ fontWeight: 700 }}>{round(r.winRate)}%</span>
                          <MiniWr winRate={r.winRate} />
                        </div>
                        <div className="note tnum" style={{ textAlign: 'right' }}>{r.wins}W {r.losses}L</div>
                      </td>
                      <td className="num">{to && <Link to={to} className="panel-link">History →</Link>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}
