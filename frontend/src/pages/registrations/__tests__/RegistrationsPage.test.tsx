import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../../../api/admin';
import { makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { RegistrationsPage } from '../RegistrationsPage';

vi.mock('../../../api/admin', () => ({
  adminApi: {
    listUsers: vi.fn(),
    approveUser: vi.fn(),
    rejectUser: vi.fn(),
    updateRole: vi.fn(),
    updateSalary: vi.fn(),
    auditLogs: vi.fn(),
  },
}));

const listUsersMock = vi.mocked(adminApi.listUsers);
const approveMock = vi.mocked(adminApi.approveUser);
const rejectMock = vi.mocked(adminApi.rejectUser);

const AWA = makeUser({ id: 1, name: 'Awa Ndiaye', email: 'awa@salarytontine.test' });
const NOUVEAU = makeUser({
  id: 3,
  name: 'Ndeye Diagne',
  email: 'ndeye@salarytontine.test',
  status: 'PENDING',
  baseSalary: 0,
});

describe('RegistrationsPage', () => {
  beforeEach(() => {
    listUsersMock.mockReset().mockResolvedValue([AWA, NOUVEAU]);
    approveMock.mockReset();
    rejectMock.mockReset();
  });

  it('ne présente que les inscriptions en attente', async () => {
    renderWithProviders(<RegistrationsPage />, { user: makeUser({ role: 'ADMIN' }) });

    expect(await screen.findByText('Ndeye Diagne')).toBeInTheDocument();
    // Les comptes déjà ouverts relèvent de la page Comptes.
    expect(screen.queryByText('Awa Ndiaye')).not.toBeInTheDocument();
  });

  it("ne propose jamais de choisir le mot de passe d'un employé", async () => {
    renderWithProviders(<RegistrationsPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Ndeye Diagne');

    expect(screen.queryByLabelText(/mot de passe/i)).not.toBeInTheDocument();
  });

  it('valide une inscription avec le rôle choisi', async () => {
    approveMock.mockResolvedValue({ ...NOUVEAU, status: 'ACTIVE', role: 'ACCOUNTANT' });
    const user = userEvent.setup();

    renderWithProviders(<RegistrationsPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Ndeye Diagne');

    await user.selectOptions(screen.getByLabelText(/rôle à attribuer/i), 'ACCOUNTANT');
    await user.type(screen.getByLabelText('Salaire de base'), '480000');
    await user.click(screen.getByRole('button', { name: /valider l'inscription/i }));

    await waitFor(() =>
      expect(approveMock).toHaveBeenCalledWith(3, { role: 'ACCOUNTANT', baseSalary: 480000 }),
    );
    expect(await screen.findByText(/est validé/i)).toBeInTheDocument();
  });

  it('refuse une inscription et la fait basculer hors de la file', async () => {
    rejectMock.mockResolvedValue({ ...NOUVEAU, status: 'REJECTED' });
    const user = userEvent.setup();

    renderWithProviders(<RegistrationsPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Ndeye Diagne');

    await user.click(screen.getByRole('button', { name: /^refuser$/i }));

    await waitFor(() => expect(rejectMock).toHaveBeenCalledWith(3));
    expect(await screen.findByText(/a été refusée/i)).toBeInTheDocument();
    // Le refus reste consultable, pour garder trace de la décision.
    expect(await screen.findByText(/inscriptions refusées/i)).toBeInTheDocument();
  });

  it('affiche un état vide quand rien n attend', async () => {
    listUsersMock.mockResolvedValue([AWA]);

    renderWithProviders(<RegistrationsPage />, { user: makeUser({ role: 'ADMIN' }) });

    expect(await screen.findByText(/aucune inscription en attente/i)).toBeInTheDocument();
  });
});
