import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import axios from 'axios';
import { tokenStore } from '../lib/api';
import { useMe, type Me } from './adminApi';

interface AuthContextValue {
  me: Me | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [hasToken, setHasToken] = useState(() => tokenStore.access !== null);
  const me = useMe(hasToken);

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await axios.post('/api/admin/auth/login', { username, password });
    tokenStore.set(data.accessToken, data.refreshToken);
    setHasToken(true);
    await me.refetch();
  }, [me]);

  const logout = useCallback(() => {
    tokenStore.clear();
    setHasToken(false);
  }, []);

  const ready = !hasToken || !me.isLoading;
  const value: AuthContextValue = {
    me: hasToken && me.data ? me.data : null,
    ready,
    login,
    logout,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAdminAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAdminAuth outside AdminAuthProvider');
  return value;
}
