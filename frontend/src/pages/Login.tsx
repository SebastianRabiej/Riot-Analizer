import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiRequestError } from '../api';
import { safeNext, useAuth } from '../auth';
import { Loading } from '../components/ui';

function errorMessage(err: unknown): string {
  if (err instanceof ApiRequestError) {
    if (err.status === 401) return 'Invalid username or password.';
    if (err.status === 403) return 'Your session token expired — reload the page and try again.';
    if (err.status === 0) return 'Could not reach the server.';
    return err.message;
  }
  return 'Login failed.';
}

/** The single admin's login form. Everything about tracking sits behind it. */
export function Login() {
  const { loading, isAdmin, status, login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (loading) return <Loading label="Checking session…" />;
  if (isAdmin) return <Navigate to={next} replace />;

  const disabled = status?.loginEnabled === false;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      setError('Enter your username and password.');
      return;
    }
    setBusy(true);
    setError(null);
    login(username, password)
      .then(() => navigate(next, { replace: true }))
      .catch((err) => {
        setError(errorMessage(err));
        setPassword('');
      })
      .finally(() => setBusy(false));
  };

  return (
    <div className="login-wrap">
      <form className="panel login-card" onSubmit={submit} noValidate>
        <div className="brand-mark">RA</div>
        <h1>Admin login</h1>
        <p className="note">Sign in to see and manage tracked players.</p>

        {disabled ? (
          <p className="login-error" role="alert">
            Admin login isn't configured on this server. Set <code>ADMIN_PASSWORD_HASH</code> and
            restart the backend.
          </p>
        ) : (
          <>
            <label className="login-field">
              <span>Username</span>
              <input
                name="username"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={busy}
                autoFocus
                maxLength={128}
              />
            </label>
            <label className="login-field">
              <span>Password</span>
              <input
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={busy}
                maxLength={256}
              />
            </label>
            {error && <p className="login-error" role="alert">{error}</p>}
            <button className="btn btn-teal login-submit" type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </>
        )}
        <Link to="/" className="panel-link" style={{ marginTop: 6 }}>← Back to home</Link>
      </form>
    </div>
  );
}
