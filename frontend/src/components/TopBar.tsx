import { useEffect, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import type { PlayerDto } from '../types';
import { getTracked } from '../api';
import { parseRiotId } from '../util';
import { useAuth } from '../auth';

export function TopBar() {
  const navigate = useNavigate();
  const { isAdmin, logout } = useAuth();
  const [input, setInput] = useState('');
  const [tracked, setTracked] = useState<PlayerDto[]>([]);
  const [err, setErr] = useState(false);

  // Tracked players are admin-only; visitors never request or see them.
  useEffect(() => {
    if (!isAdmin) {
      setTracked([]);
      return;
    }
    getTracked().then(setTracked).catch(() => setTracked([]));
  }, [isAdmin]);

  const doLogout = () => {
    logout()
      .catch(() => undefined)
      .finally(() => navigate('/'));
  };

  const go = (gameName: string, tagLine: string) =>
    navigate(`/player/${encodeURIComponent(gameName)}/${encodeURIComponent(tagLine)}`);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseRiotId(input);
    if (!parsed) {
      setErr(true);
      return;
    }
    setErr(false);
    setInput('');
    go(parsed.gameName, parsed.tagLine);
  };

  return (
    <header className="topbar">
      <div className="container topbar-inner">
        <Link to="/" className="brand">
          <span className="brand-mark">RA</span>
          Riot Analizer
        </Link>

        <div className="topbar-spacer" />

        {isAdmin && (
        <div className="tracked-chips">
          {tracked.slice(0, 3).map((p) => (
            <button
              key={p.puuid}
              className="chip"
              onClick={() => go(p.gameName, p.tagLine)}
              title={`${p.gameName}#${p.tagLine}`}
              type="button"
            >
              {p.gameName}
            </button>
          ))}
          <NavLink
            to="/tracked"
            className={({ isActive }) => `chip ${isActive ? 'active' : ''}`}
            title="Manage tracked players"
          >
            ⚙ Tracking
          </NavLink>
        </div>
        )}

        <div className="auth-slot">
          {isAdmin ? (
            <button type="button" className="chip" onClick={doLogout} title="Sign out of the admin session">
              Log out
            </button>
          ) : (
            <NavLink
              to="/login"
              className={({ isActive }) => `chip ${isActive ? 'active' : ''}`}
              title="Admin login"
            >
              🔒 Admin
            </NavLink>
          )}
        </div>

        <form className={`search ${err ? 'error' : ''}`} onSubmit={submit}>
          <input
            placeholder="Riot ID  e.g.  Faker#KR1"
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              if (err) setErr(false);
            }}
            aria-label="Search a Riot ID"
          />
          <button type="submit">Search</button>
        </form>
      </div>
    </header>
  );
}
