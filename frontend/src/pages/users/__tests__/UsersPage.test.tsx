import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from '../../../api/admin';
import { makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { UsersPage } from '../UsersPage';

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
const updateRoleMock = vi.mocked(adminApi.updateRole);
const updateSalaryMock = vi.mocked(adminApi.updateSalary);

const AWA = makeUser({ id: 1, name: 'Awa Ndiaye', email: 'awa@salarytontine.test', baseSalary: 500000 });
const NOUVEAU = makeUser({
  id: 3,
  name: 'Ndeye Diagne',
  email: 'ndeye@salarytontine.test',
  status: 'PENDING',
  baseSalary: 0,
});

describe('UsersPage', () => {
  beforeEach(() => {
    listUsersMock.mockReset().mockResolvedValue([AWA, NOUVEAU]);
    approveMock.mockReset();
    rejectMock.mockReset();
    updateRoleMock.mockReset();
    updateSalaryMock.mockReset();
  });

  it('envoie le nouveau rôle à l API', async () => {
    updateRoleMock.mockResolvedValue({ ...AWA, role: 'ACCOUNTANT' });
    const user = userEvent.setup();

    renderWithProviders(<UsersPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Awa Ndiaye');

    await user.selectOptions(screen.getByLabelText(/rôle de awa ndiaye/i), 'ACCOUNTANT');

    await waitFor(() => expect(updateRoleMock).toHaveBeenCalledWith(1, 'ACCOUNTANT'));
  });

  it('envoie la nouvelle valeur de salaire à l API', async () => {
    updateSalaryMock.mockResolvedValue({ ...AWA, baseSalary: 600000 });
    const user = userEvent.setup();

    renderWithProviders(<UsersPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Awa Ndiaye');

    const salaryInput = screen.getByLabelText(/salaire de base de awa ndiaye/i);
    await user.clear(salaryInput);
    await user.type(salaryInput, '600000');
    await user.click(screen.getByRole('button', { name: /enregistrer/i }));

    await waitFor(() => expect(updateSalaryMock).toHaveBeenCalledWith(1, 600000));
  });

  it('renvoie vers la page Inscriptions sans les traiter ici', async () => {
    renderWithProviders(<UsersPage />, { user: makeUser({ role: 'ADMIN' }) });
    await screen.findByText('Awa Ndiaye');

    expect(screen.getByText(/attend votre validation/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /les traiter/i })).toBeInTheDocument();
    // La validation ne se fait pas depuis cette page.
    expect(screen.queryByRole('button', { name: /valider l'inscription/i })).not.toBeInTheDocument();
  });
});
