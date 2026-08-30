import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../test/renderWithProviders';
import { makeUser } from '../../test/factories';
import { AppLayout } from '../AppLayout';

describe('AppLayout - navigation selon le role', () => {
  it('n offre a un EMPLOYEE que les entrees generales', () => {
    renderWithProviders(<AppLayout />, { user: makeUser({ role: 'EMPLOYEE' }) });

    expect(screen.getByRole('link', { name: /tableau de bord/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^tontines$/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /mon salaire/i })).toBeInTheDocument();

    expect(screen.queryByRole('link', { name: /demandes/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /employés et salaires/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^comptes$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /inscriptions/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /journal d'audit/i })).not.toBeInTheDocument();
  });

  it('ouvre la gestion a un ACCOUNTANT mais pas l administration des comptes', () => {
    renderWithProviders(<AppLayout />, { user: makeUser({ role: 'ACCOUNTANT' }) });

    expect(screen.getByRole('link', { name: /demandes/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /employés et salaires/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^comptes$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /journal d'audit/i })).not.toBeInTheDocument();
  });

  it('ouvre tout a un ADMIN', () => {
    renderWithProviders(<AppLayout />, { user: makeUser({ role: 'ADMIN' }) });

    expect(screen.getByRole('link', { name: /demandes/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /employés et salaires/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^comptes$/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /inscriptions/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /journal d'audit/i })).toBeInTheDocument();
  });

  it('affiche le nom et le role de l utilisateur connecte', () => {
    renderWithProviders(<AppLayout />, { user: makeUser({ name: 'Awa Ndiaye', role: 'EMPLOYEE' }) });

    expect(screen.getByText('Awa Ndiaye')).toBeInTheDocument();
    expect(screen.getByText('Employé')).toBeInTheDocument();
  });

  it('affiche la marque en pied de page', () => {
    renderWithProviders(<AppLayout />, { user: makeUser() });

    expect(screen.getByText(/SalaryTontine —/)).toBeInTheDocument();
  });
});
