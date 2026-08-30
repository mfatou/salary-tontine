import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { tontineApi } from '../../../api/tontines';
import { adminApi } from '../../../api/admin';
import { makeSchedule, makeTontine, makeTontineDetail, makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { TontineDetailPage } from '../TontineDetailPage';

vi.mock('../../../api/tontines', () => ({
  tontineApi: {
    get: vi.fn(),
    schedule: vi.fn(),
    listContributions: vi.fn(),
    activate: vi.fn(),
    addMember: vi.fn(),
    removeMember: vi.fn(),
    generateContributions: vi.fn(),
    generateSalaries: vi.fn(),
  },
}));

vi.mock('../../../api/admin', () => ({
  adminApi: { listUsers: vi.fn() },
}));

const getMock = vi.mocked(tontineApi.get);
const scheduleMock = vi.mocked(tontineApi.schedule);
const contributionsMock = vi.mocked(tontineApi.listContributions);
const activateMock = vi.mocked(tontineApi.activate);

function renderDetail(user = makeUser(), status: 'DRAFT' | 'ACTIVE' = 'ACTIVE') {
  getMock.mockResolvedValue(makeTontineDetail({ tontine: makeTontine({ status }) }));
  scheduleMock.mockResolvedValue(makeSchedule());
  contributionsMock.mockResolvedValue([]);
  vi.mocked(adminApi.listUsers).mockResolvedValue([]);

  return renderWithProviders(
    <Routes>
      <Route path="/tontines/:id" element={<TontineDetailPage />} />
    </Routes>,
    { user, route: '/tontines/10' },
  );
}

describe('TontineDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('affiche les informations et le calendrier de la tontine', async () => {
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Tontine Equipe A' })).toBeInTheDocument();
    expect(screen.getAllByText(/250\s?000\sFCFA/).length).toBeGreaterThan(0);
    // Le calendrier affiche désormais les bornes du tour, pas un mois.
    // La date apparaît dans l'en-tête et dans le calendrier : on compte les deux.
    expect(screen.getAllByText(/1 août 2026/).length).toBeGreaterThan(0);
    expect(screen.getByText(/1 septembre 2026/)).toBeInTheDocument();
  });

  it('masque les actions de gestion a un EMPLOYEE', async () => {
    renderDetail(makeUser({ role: 'EMPLOYEE' }), 'DRAFT');

    await screen.findByRole('heading', { name: 'Tontine Equipe A' });

    expect(screen.queryByRole('button', { name: /activer la tontine/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /ajouter/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retirer/i })).not.toBeInTheDocument();
  });

  it('propose l activation a un ACCOUNTANT sur une tontine DRAFT', async () => {
    renderDetail(makeUser({ role: 'ACCOUNTANT' }), 'DRAFT');

    expect(await screen.findByRole('button', { name: /activer la tontine/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ajouter/i })).toBeInTheDocument();
  });

  it('propose la génération mensuelle a un ACCOUNTANT sur une tontine ACTIVE', async () => {
    renderDetail(makeUser({ role: 'ACCOUNTANT' }), 'ACTIVE');

    expect(await screen.findByRole('button', { name: /générer les cotisations/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /générer les salaires/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /activer la tontine/i })).not.toBeInTheDocument();
  });

  it('demande confirmation avant d activer la tontine', async () => {
    const user = userEvent.setup();
    activateMock.mockResolvedValue(makeTontine({ status: 'ACTIVE' }));
    renderDetail(makeUser({ role: 'ACCOUNTANT' }), 'DRAFT');

    await user.click(await screen.findByRole('button', { name: /activer la tontine/i }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(/ne pourront plus être modifies/i);
    expect(activateMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Activer' }));
    expect(activateMock).toHaveBeenCalledWith(10);
  });

  it('demande confirmation avant une génération mensuelle', async () => {
    const user = userEvent.setup();
    renderDetail(makeUser({ role: 'ACCOUNTANT' }), 'ACTIVE');

    await user.click(await screen.findByRole('button', { name: /générer les salaires/i }));

    expect(await screen.findByRole('dialog')).toHaveTextContent(/ne peut pas être annulée/i);
    expect(tontineApi.generateSalaries).not.toHaveBeenCalled();
  });
});
