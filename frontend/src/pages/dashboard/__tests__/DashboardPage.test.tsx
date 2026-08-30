import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { employeeApi } from '../../../api/employees';
import { dashboardApi } from '../../../api/salaries';
import { tontineApi } from '../../../api/tontines';
import { makeDashboard, makeSalaryRecord, makeSchedule, makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { DashboardPage } from '../DashboardPage';

vi.mock('../../../api/salaries', () => ({
  dashboardApi: { load: vi.fn() },
  salaryApi: { mine: vi.fn() },
}));

// Le tableau de bord agrege plusieurs sources selon le role : sans ces mocks,
// les appels partiraient reellement sur le reseau depuis jsdom.
vi.mock('../../../api/tontines', () => ({
  tontineApi: {
    schedule: vi.fn(),
    myJoinRequests: vi.fn(),
    pendingJoinRequests: vi.fn(),
    list: vi.fn(),
  },
}));

vi.mock('../../../api/employees', () => ({
  employeeApi: { list: vi.fn() },
}));

const loadMock = vi.mocked(dashboardApi.load);
const scheduleMock = vi.mocked(tontineApi.schedule);
const myRequestsMock = vi.mocked(tontineApi.myJoinRequests);
const pendingMock = vi.mocked(tontineApi.pendingJoinRequests);
const listTontinesMock = vi.mocked(tontineApi.list);
const employeesMock = vi.mocked(employeeApi.list);

describe('DashboardPage', () => {
  beforeEach(() => {
    loadMock.mockReset();
    scheduleMock.mockReset().mockResolvedValue(makeSchedule());
    myRequestsMock.mockReset().mockResolvedValue([]);
    pendingMock.mockReset().mockResolvedValue([]);
    listTontinesMock.mockReset().mockResolvedValue([]);
    employeesMock.mockReset().mockResolvedValue([]);
  });

  it('affiche le salaire simule du bénéficiaire', async () => {
    loadMock.mockResolvedValue(makeDashboard());

    renderWithProviders(<DashboardPage />, { user: makeUser({ name: 'Awa Ndiaye' }) });

    expect(await screen.findByText(/bonjour awa ndiaye/i)).toBeInTheDocument();
    expect(screen.getByText(/500\s?000\sFCFA/)).toBeInTheDocument();
    expect(screen.getByText(/-50\s?000\sFCFA/)).toBeInTheDocument();
    expect(screen.getByText(/\+250\s?000\sFCFA/)).toBeInTheDocument();
    expect(screen.getByText(/700\s?000\sFCFA/)).toBeInTheDocument();
  });

  it('affiche la tontine active, la position et le prochain bénéficiaire', async () => {
    loadMock.mockResolvedValue(makeDashboard());

    renderWithProviders(<DashboardPage />, { user: makeUser() });

    expect(await screen.findByText('Tontine Equipe A')).toBeInTheDocument();
    expect(screen.getByText('1 / 5')).toBeInTheDocument();
    // La date du tour remplace le mois : une tontine peut tourner à la semaine.
    expect(screen.getByText('1 août 2026')).toBeInTheDocument();
    expect(screen.getByText(/Awa Ndiaye \(1 août 2026\)/)).toBeInTheDocument();
  });

  it('affiche un etat vide propre sans tontine active', async () => {
    loadMock.mockResolvedValue(
      makeDashboard({
        activeTontine: null,
        myTurnOrder: null,
        myTurnDate: null,
        nextBeneficiary: null,
        latestSalaryRecord: null,
      }),
    );

    renderWithProviders(<DashboardPage />, { user: makeUser() });

    expect(await screen.findByText(/aucune tontine active/i)).toBeInTheDocument();
    expect(screen.getByText(/aucune cotisation enregistree/i)).toBeInTheDocument();
  });

  it('n affiche pas de mention de bénéficiaire pour un non-bénéficiaire', async () => {
    loadMock.mockResolvedValue(
      makeDashboard({
        latestSalaryRecord: makeSalaryRecord({
          tontineReceived: 0,
          finalSalary: 450000,
          beneficiary: false,
        }),
      }),
    );

    renderWithProviders(<DashboardPage />, { user: makeUser() });

    expect(await screen.findByText(/450\s?000\sFCFA/)).toBeInTheDocument();
    expect(screen.queryByText(/vous etes bénéficiaire/i)).not.toBeInTheDocument();
  });

  it('affiche un message d erreur si l API echoue', async () => {
    loadMock.mockRejectedValue(new Error('boom'));

    renderWithProviders(<DashboardPage />, { user: makeUser() });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('remplace la synthese salariale par le pilotage pour un comptable', async () => {
    loadMock.mockResolvedValue(makeDashboard({ activeTontine: null, latestSalaryRecord: null }));
    pendingMock.mockResolvedValue([
      { ...makeDashboard().user, id: 1 } as never,
      { ...makeDashboard().user, id: 2 } as never,
    ]);

    renderWithProviders(<DashboardPage />, { user: makeUser({ role: 'ACCOUNTANT' }) });

    expect(await screen.findByText(/pilotage/i)).toBeInTheDocument();
    // Chaine exacte : une regex matcherait aussi la carte entiere, qui contient ce libelle.
    expect(screen.getByText('Demandes en attente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /traiter les demandes/i })).toBeInTheDocument();
    // Le comptable n'a pas de salaire fictif : la synthese salariale ne le concerne pas.
    expect(screen.queryByText(/cotisation du mois/i)).not.toBeInTheDocument();
  });

  it('liste les demandes en cours d un employé', async () => {
    loadMock.mockResolvedValue(makeDashboard({ activeTontine: null, latestSalaryRecord: null }));
    myRequestsMock.mockResolvedValue([
      {
        id: 9,
        tontineId: 3,
        tontineName: 'Tontine Septembre',
        userId: 1,
        userName: 'Awa Ndiaye',
        userEmail: 'awa@salarytontine.test',
        status: 'PENDING',
        motivation: null,
        decisionNote: null,
        requestedAt: '2026-08-26T10:00:00Z',
        decidedAt: null,
        decidedByName: null,
      },
    ]);

    renderWithProviders(<DashboardPage />, { user: makeUser() });

    expect(await screen.findByText('Tontine Septembre')).toBeInTheDocument();
    expect(screen.getByText(/en attente/i)).toBeInTheDocument();
  });
});
