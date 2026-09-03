import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { getTracked } from '../api';
import { parseRiotId } from '../util';
import { Loading } from '../components/ui';

export function Landing() {
  const navigate = useNavigate();
  const [tracked, setTracked] = useState<PlayerDto[] | null>(null);
  const [input, setInput] = useState('');
  const [err, setErr] = useState(false);

  useEffect(() => {
    let active = true;
    getTracked()
      .then((list) => {
        if (!active) return;
        if (list.length > 0) {
          const p = list[0];
          navigate(
            `/player/${encodeURIComponent(p.gameName)}/${encodeURIComponent(p.tagLine)}`,
            { replace: true },
          );
        } else {
          navigate('/tracked', { replace: true });
        }
      })
      .catch(() => active && setTracked([]));
    return () => {
      active = false;
    };
  }, [navigate]);

  if (tracked === null) return <Loading label="Loading…" />;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseRiotId(input);
    if (!parsed) {
      setErr(true);
      return;
    }
    navigate(`/player/${encodeURIComponent(parsed.gameName)}/${encodeURIComponent(parsed.tagLine)}`);
  };

  return (
    <div className="landing">
      <div className="brand-mark">RA</div>
      <h1>Riot Analizer</h1>
      <p>
        Your personal League companion. Track a player and dig into champions, matchups, teammates,
        roles, timing — and check whether you've crossed paths with anyone before.
      </p>
      <form className={`search block ${err ? 'error' : ''}`} onSubmit={submit} style={{ maxWidth: 380, margin: '0 auto' }}>
        <input
          placeholder="Riot ID  e.g.  Faker#KR1"
          value={input}
          onChange={(e) => {
            setInput(e.target.value);
            if (err) setErr(false);
          }}
          style={{ width: '100%' }}
        />
        <button type="submit">Track</button>
      </form>
      {err && <p style={{ color: 'var(--loss)', marginTop: 10 }}>Enter a Riot ID as gameName#tagLine.</p>}
    </div>
  );
}
