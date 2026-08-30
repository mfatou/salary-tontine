import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { salaryApi } from '../../../api/salaries';
import { makeSalaryRecord, makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { MySalaryPage } from '../MySalaryPage';

vi.mock('../../../api/salaries', () => ({
  salaryApi: { mine: vi.fn() },
  dashboardApi: { load: vi.fn() },
}));

const mineMock = vi.mocked(salaryApi.mine);

describe('MySalaryPage', () => {
  beforeEach(() => {
    mineMock.mockReset();
  });

  it('affiche l historique mensuel des salaires simules', async () => {
    mineMock.mockResolvedValue([
      makeSalaryRecord({ id: 1, salaryMonth: '2026-09', tontineReceived: 0, finalSalary: 450000, beneficiary: false }),
      makeSalaryRecord({ id: 2, salaryMonth: '2026-08' }),
    ]);

    renderWithProviders(<MySalaryPage />, { user: makeUser() });

    expect(await screen.findByText('septembre 2026')).toBeInTheDocument();
    expect(screen.getByText('août 2026')).toBeInTheDocument();
    expect(screen.getAllByText('Tontine Equipe A')).toHaveLength(2);
  });

  it('affiche le détail du calcul pour un mois bénéficiaire', async () => {
    mineMock.mockResolvedValue([makeSalaryRecord()]);

    renderWithProviders(<MySalaryPage />, { user: makeUser() });

    expect(await screen.findByText('août 2026')).toBeInTheDocument();
    expect(screen.getAllByText(/-50\s?000\sFCFA/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/\+250\s?000\sFCFA/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/700\s?000\sFCFA/).length).toBeGreaterThan(0);
  });

  it('affiche un etat vide quand aucun salaire n existe', async () => {
    mineMock.mockResolvedValue([]);

    renderWithProviders(<MySalaryPage />, { user: makeUser() });

    expect(await screen.findByText(/aucun salaire simule enregistre/i)).toBeInTheDocument();
  });

  it('remonte une erreur de l API', async () => {
    mineMock.mockRejectedValue(new Error('indisponible'));

    renderWithProviders(<MySalaryPage />, { user: makeUser() });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
