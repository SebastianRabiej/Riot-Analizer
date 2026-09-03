import { useState } from 'react';
import { getMatches } from '../api';
import { useAsync } from '../hooks';
import { usePlayerCtx } from '../playerContext';
import { Panel, Loading, ErrorBox, EmptyState } from '../components/ui';
import { MatchRow } from '../components/MatchRow';

const SIZE = 20;

export function Matches() {
  const { gameName, tagLine, queue, refreshKey } = usePlayerCtx();
  const [page, setPage] = useState(0);

  const { data, loading, error } = useAsync(
    () => getMatches(gameName, tagLine, page, SIZE, queue),
    [gameName, tagLine, page, queue, refreshKey],
  );

  const totalPages = data?.totalPages ?? 0;

  return (
    <Panel
      title="Match history"
      sub={data ? `${data.totalElements} games this season` : undefined}
      action={
        data && totalPages > 1 ? (
          <div className="row" style={{ gap: 8 }}>
            <button className="btn btn-sm" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>← Prev</button>
            <span className="note tnum">Page {page + 1} / {totalPages}</span>
            <button className="btn btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>Next →</button>
          </div>
        ) : undefined
      }
    >
      {loading && !data && <Loading label="Loading matches…" />}
      {error && <ErrorBox message={error} />}
      {data && data.content.length === 0 && (
        <EmptyState emoji="📜" message="No matches for this filter" hint="Try a different queue filter, or Refresh to pull the latest games." />
      )}
      {data && data.content.length > 0 && (
        <div className="stack" style={{ gap: 8 }}>
          {data.content.map((m) => <MatchRow key={m.matchId} m={m} />)}
        </div>
      )}
    </Panel>
  );
}
