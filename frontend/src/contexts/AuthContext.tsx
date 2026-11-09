import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { refreshSession, setAccessToken, setSessionLostHandler } from '@/services/api';
import { authApi } from '@/services/endpoints';
import type { AuthResponse, User } from '@/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (name: string, email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  const accept = useCallback((response: AuthResponse) => {
    setAccessToken(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  useEffect(() => {
    setSessionLostHandler(() => {
      setUser(null);
      queryClient.clear();
    });
    // The access token is memory-only, so each page load restores the session from the refresh cookie.
    refreshSession()
      .then((response) => setUser(response.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
    return () => setSessionLostHandler(null);
  }, [queryClient]);

  const login = useCallback(
    async (email: string, password: string) => accept(await authApi.login({ email, password })),
    [accept],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => accept(await authApi.register({ name, email, password })),
    [accept],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, setUser }),
    [user, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
