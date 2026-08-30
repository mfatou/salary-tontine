import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import type { LoginPayload, RegisterPayload, Role, User } from '../types';
import { AuthContext, type AuthContextValue } from './AuthContext';

/**
 * Source de verite de la session cote client.
 *
 * L'etat est reconstruit au demarrage en interrogeant `/api/auth/me` : le jeton
 * etant dans un cookie HttpOnly, le client ne peut pas le lire lui-même.
 * Ce contexte ne sert qu'a l'affichage ; l'autorisation réelle est appliquee
 * par le backend a chaque requete.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me());
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(async (payload: LoginPayload) => {
    const loggedIn = await authApi.login(payload);
    setUser(loggedIn);
    return loggedIn;
  }, []);

  const register = useCallback((payload: RegisterPayload) => authApi.register(payload), []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, login, register, logout, refresh, hasRole }),
    [user, loading, login, register, logout, refresh, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
