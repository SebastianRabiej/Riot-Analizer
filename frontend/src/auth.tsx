import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { AuthStatus } from './types';
import {
  AUTH_EXPIRED_EVENT,
  getAuthStatus,
  login as apiLogin,
  logout as apiLogout,
} from './api';
import { Loading } from './components/ui';

interface AuthContextValue {
  /** null until the first /auth/me answer arrives. */
  status: AuthStatus | null;
  loading: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const SIGNED_OUT: AuthStatus = { authenticated: false, username: null, loginEnabled: true };

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the admin session state. The session itself lives in an HttpOnly
 * cookie the page can't read — this only mirrors what the server reports.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus | null>(null);

  const recheck = useCallback(() => {
    getAuthStatus()
      .then(setStatus)
      .catch(() => setStatus((s) => ({ ...(s ?? SIGNED_OUT), authenticated: false, username: null })));
  }, []);

  useEffect(() => {
    recheck();
    window.addEventListener(AUTH_EXPIRED_EVENT, recheck);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, recheck);
  }, [recheck]);

  const login = useCallback(async (username: string, password: string) => {
    setStatus(await apiLogin(username, password));
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      // Re-ask the server: also picks up the fresh CSRF cookie.
      recheck();
      setStatus((s) => ({ ...(s ?? SIGNED_OUT), authenticated: false, username: null }));
    }
  }, [recheck]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      loading: status === null,
      isAdmin: status?.authenticated === true,
      login,
      logout,
    }),
    [status, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/** Route guard: renders children only for the logged-in admin, else sends to /login. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { loading, isAdmin } = useAuth();
  const location = useLocation();
  if (loading) return <Loading label="Checking session…" />;
  if (!isAdmin) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <>{children}</>;
}

/** Only allow in-app redirect targets after login (no "//evil.com" style URLs). */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) {
    return '/tracked';
  }
  return next;
}
