import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../../../api/admin';
import { makeAuditLog } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { makeUser } from '../../../test/factories';
import { AuditPage } from '../AuditPage';

vi.mock('../../../api/admin', () => ({
  adminApi: { auditLogs: vi.fn() },
}));

const auditLogsMock = vi.mocked(adminApi.auditLogs);

function page(content: ReturnType<typeof makeAuditLog>[]) {
  return { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 };
}

describe('AuditPage', () => {
  beforeEach(() => {
    auditLogsMock.mockReset();
  });

  it('affiche les traces avec leur libelle francais', async () => {
    auditLogsMock.mockResolvedValue(
      page([makeAuditLog({ id: 1, action: 'TONTINE_ACTIVATED', userName: 'Comptable Demo' })]),
    );

    renderWithProviders(<AuditPage />, { user: makeUser({ role: 'ADMIN' }) });

    // Le libelle figure aussi dans la liste deroulante du filtre : on restreint
    // la recherche au tableau des traces.
    const table = within(await screen.findByRole('table'));
    expect(table.getByText('Tontine activée')).toBeInTheDocument();
    expect(table.getByText('Comptable Demo')).toBeInTheDocument();
  });

  it('designe le planificateur comme auteur des traces système', async () => {
    auditLogsMock.mockResolvedValue(
      page([
        makeAuditLog({
          id: 2,
          action: 'SALARIES_GENERATED',
          userId: null,
          userName: 'Système',
        }),
      ]),
    );

    renderWithProviders(<AuditPage />, { user: makeUser({ role: 'ADMIN' }) });

    const table = within(await screen.findByRole('table'));
    expect(table.getByText('Système')).toBeInTheDocument();
    expect(table.getByText('Salaires générés')).toBeInTheDocument();
  });

  it('affiche un etat vide explicite', async () => {
    auditLogsMock.mockResolvedValue(page([]));

    renderWithProviders(<AuditPage />, { user: makeUser({ role: 'ADMIN' }) });

    expect(await screen.findByText(/aucune trace d'audit/i)).toBeInTheDocument();
  });
});
