import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api } from './api';
import { tokenStore } from './tokenStore';
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage';
import type { MeResponse, RoleCode, ScopeInfo, TokenResponse, UserInfo } from './types';

interface AuthContextValue {
  user: UserInfo | null;
  scope: ScopeInfo | null;
  initializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** clears the forced-change state after a successful password change */
  passwordChanged: () => void;
  hasAnyRole: (...roles: RoleCode[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [scope, setScope] = useState<ScopeInfo | null>(null);
  const [initializing, setInitializing] = useState(() => tokenStore.getAccess() !== null);

  // Revalidate the session on mount when a token is present.
  useEffect(() => {
    if (!tokenStore.getAccess()) {
      return;
    }
    api
      .get<MeResponse>('/auth/me')
      .then(({ data }) => {
        setUser(data.user);
        setScope(data.scope);
      })
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await api.post<TokenResponse>('/auth/login', { username, password });
    tokenStore.set(data.accessToken, data.refreshToken);
    setUser(data.user);
    const me = await api.get<MeResponse>('/auth/me');
    setScope(me.data.scope);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // best effort — tokens are cleared regardless
    }
    tokenStore.clear();
    setUser(null);
    setScope(null);
  }, []);

  const hasAnyRole = useCallback(
    (...roles: RoleCode[]) => user !== null && roles.some((role) => user.roles.includes(role)),
    [user],
  );

  const passwordChanged = useCallback(
    () => setUser((current) => (current ? { ...current, mustChangePassword: false } : current)),
    [],
  );

  const value = useMemo(
    () => ({ user, scope, initializing, login, logout, passwordChanged, hasAnyRole }),
    [user, scope, initializing, login, logout, passwordChanged, hasAnyRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}

/** Route guard: unauthenticated users are sent to the login screen. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth();
  const location = useLocation();
  if (initializing) {
    return null;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (user.mustChangePassword) {
    return <ChangePasswordPage forced />;
  }
  return <>{children}</>;
}

/** Route guard for role-restricted areas; out-of-role users see the dashboard. */
export function RequireRole({ roles, children }: { roles: RoleCode[]; children: ReactNode }) {
  const { hasAnyRole } = useAuth();
  if (!hasAnyRole(...roles)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
