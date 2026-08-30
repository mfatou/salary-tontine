import { createContext } from 'react';
import type { LoginPayload, RegisterPayload, Role, User } from '../types';

export interface AuthContextValue {
  user: User | null;
  /** Vrai tant que la session n'a pas été resolue au premier chargement. */
  loading: boolean;
  login: (payload: LoginPayload) => Promise<User>;
  register: (payload: RegisterPayload) => Promise<User>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
