import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext, type AuthContextValue } from '../context/AuthContext';
import type { Role, User } from '../types';

interface ProviderOptions extends Omit<RenderOptions, 'wrapper'> {
  user?: User | null;
  loading?: boolean;
  route?: string;
}

/**
 * Rend un composant avec un contexte d'authentification controle,
 * ce qui permet de tester le comportement propre a chaque role.
 */
export function renderWithProviders(ui: ReactElement, options: ProviderOptions = {}) {
  const { user = null, loading = false, route = '/', ...renderOptions } = options;

  const authValue: AuthContextValue = {
    user,
    loading,
    login: vi.fn().mockResolvedValue(user),
    register: vi.fn().mockResolvedValue(user),
    logout: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
    hasRole: (...roles: Role[]) => (user ? roles.includes(user.role) : false),
  };

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={[route]}>
        <AuthContext.Provider value={authValue}>{children}</AuthContext.Provider>
      </MemoryRouter>
    );
  }

  return { ...render(ui, { wrapper: Wrapper, ...renderOptions }), authValue };
}
