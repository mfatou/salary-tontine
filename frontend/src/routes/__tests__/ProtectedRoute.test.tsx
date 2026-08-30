import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '../../test/renderWithProviders';
import { makeUser } from '../../test/factories';
import { ProtectedRoute } from '../ProtectedRoute';
import { RoleProtectedRoute } from '../RoleProtectedRoute';

function ProtectedTree() {
  return (
    <Routes>
      <Route path="/login" element={<p>Page de connexion</p>} />
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<p>Contenu prive</p>} />
      </Route>
    </Routes>
  );
}

function AdminTree() {
  return (
    <Routes>
      <Route path="/login" element={<p>Page de connexion</p>} />
      <Route path="/forbidden" element={<p>Accès refusé</p>} />
      <Route element={<RoleProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route path="/admin" element={<p>Console administrateur</p>} />
      </Route>
    </Routes>
  );
}

describe('ProtectedRoute', () => {
  it('redirige un visiteur non authentifie vers la page de connexion', () => {
    renderWithProviders(<ProtectedTree />, { user: null, route: '/dashboard' });

    expect(screen.getByText('Page de connexion')).toBeInTheDocument();
    expect(screen.queryByText('Contenu prive')).not.toBeInTheDocument();
  });

  it('laisse passer un utilisateur authentifie', () => {
    renderWithProviders(<ProtectedTree />, { user: makeUser(), route: '/dashboard' });

    expect(screen.getByText('Contenu prive')).toBeInTheDocument();
  });

  it('attend la resolution de la session avant de rediriger', () => {
    renderWithProviders(<ProtectedTree />, { user: null, loading: true, route: '/dashboard' });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('Page de connexion')).not.toBeInTheDocument();
  });
});

describe('RoleProtectedRoute', () => {
  it('interdit la console administrateur a un EMPLOYEE', () => {
    renderWithProviders(<AdminTree />, { user: makeUser({ role: 'EMPLOYEE' }), route: '/admin' });

    expect(screen.getByText('Accès refusé')).toBeInTheDocument();
    expect(screen.queryByText('Console administrateur')).not.toBeInTheDocument();
  });

  it('interdit la console administrateur a un ACCOUNTANT', () => {
    renderWithProviders(<AdminTree />, { user: makeUser({ role: 'ACCOUNTANT' }), route: '/admin' });

    expect(screen.getByText('Accès refusé')).toBeInTheDocument();
  });

  it('autorise la console administrateur a un ADMIN', () => {
    renderWithProviders(<AdminTree />, { user: makeUser({ role: 'ADMIN' }), route: '/admin' });

    expect(screen.getByText('Console administrateur')).toBeInTheDocument();
  });

  it('redirige vers la connexion si la session est absente', () => {
    renderWithProviders(<AdminTree />, { user: null, route: '/admin' });

    expect(screen.getByText('Page de connexion')).toBeInTheDocument();
  });
});
