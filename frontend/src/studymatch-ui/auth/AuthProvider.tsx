import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { nextStep, type AuthAdapter, type AuthStep, type AuthUser, type ChangePasswordResult, type LoginResult, type Role, type Session } from './types';

export interface AuthContextValue {
  status: 'loading' | 'authenticated' | 'anonymous';
  session: Session | null;
  user: AuthUser | null;
  /** Next mandatory step for this session (GR1, first login, email verification). */
  step: AuthStep;
  adapter: AuthAdapter;
  login: (email: string, password: string) => Promise<LoginResult>;
  logout: () => Promise<void>;
  changePassword: (input: { current?: string; next: string }) => Promise<ChangePasswordResult>;
  /** Re-read the session from the adapter. */
  refresh: () => Promise<void>;
  /** Patch the user locally after a step, e.g. `{ hasPersonalityTest: true }`. */
  updateUser: (patch: Partial<AuthUser>) => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface AuthProviderProps {
  adapter: AuthAdapter;
  children: ReactNode;
  /** Ask for a verified personal email before entering the app. */
  requirePersonalEmail?: boolean;
  /** Called after logout (e.g. navigate to /login and show a toast). */
  onLoggedOut?: () => void;
}

/**
 * Holds the session for the whole app and exposes it through `useAuth()`.
 * Concurrent calls are safe: stale responses never overwrite newer state.
 */
export function AuthProvider({ adapter, children, requirePersonalEmail = true, onLoggedOut }: AuthProviderProps) {
  const [session, setSession] = useState<Session | null>(null);
  const [status, setStatus] = useState<AuthContextValue['status']>('loading');
  const generation = useRef(0);
  const onLoggedOutRef = useRef(onLoggedOut);
  onLoggedOutRef.current = onLoggedOut;

  const apply = useCallback((s: Session | null) => {
    setSession(s);
    setStatus(s ? 'authenticated' : 'anonymous');
  }, []);

  const refresh = useCallback(async () => {
    const g = ++generation.current;
    const s = await adapter.getSession().catch(() => null);
    if (g === generation.current) apply(s);
  }, [adapter, apply]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(
    async (email: string, password: string) => {
      const g = ++generation.current;
      const result = await adapter.login(email.trim(), password);
      if (result.status === 'ok' && g === generation.current) apply(result.session);
      return result;
    },
    [adapter, apply],
  );

  const logout = useCallback(async () => {
    ++generation.current;
    await adapter.logout().catch(() => undefined);
    apply(null);
    onLoggedOutRef.current?.();
  }, [adapter, apply]);

  const changePassword = useCallback(
    async (input: { current?: string; next: string }) => {
      const r = await adapter.changePassword(input);
      if (r.ok) setSession((s) => (s ? { ...s, mustChangePassword: false } : s));
      return r;
    },
    [adapter],
  );

  const updateUser = useCallback((patch: Partial<AuthUser>) => setSession((s) => (s ? { ...s, user: { ...s.user, ...patch } } : s)), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      user: session?.user ?? null,
      step: status === 'loading' ? 'login' : nextStep(session, { requirePersonalEmail }),
      adapter,
      login,
      logout,
      changePassword,
      refresh,
      updateUser,
      hasRole: (...roles: Role[]) => !!session && roles.includes(session.user.role),
    }),
    [status, session, requirePersonalEmail, adapter, login, logout, changePassword, refresh, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('[StudyMatch UI] useAuth() must be used inside <AuthProvider>.');
  return ctx;
}

/** Same as useAuth() but returns null outside a provider (blocks use this to stay usable standalone). */
export function useOptionalAuth(): AuthContextValue | null {
  return useContext(AuthContext);
}

export interface RequireAuthProps {
  /** Allowed roles. Omit to allow any signed-in user. */
  roles?: Role[];
  children: ReactNode;
  /** Rendered while the session loads. */
  loading?: ReactNode;
  /** Rendered when signed out. With a router, render <Navigate to="/login" />. */
  fallback?: ReactNode;
  /** Rendered when signed in with the wrong role. */
  forbidden?: ReactNode;
}

/** Router-agnostic gate for pages and sections. */
export function RequireAuth({ roles, children, loading = null, fallback = null, forbidden = null }: RequireAuthProps) {
  const { status, user } = useAuth();
  if (status === 'loading') return <>{loading}</>;
  if (!user) return <>{fallback}</>;
  if (roles && !roles.includes(user.role)) return <>{forbidden}</>;
  return <>{children}</>;
}
