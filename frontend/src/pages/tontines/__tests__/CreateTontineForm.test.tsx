import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { tontineApi } from '../../../api/tontines';
import { makeTontine, makeUser } from '../../../test/factories';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { CreateTontineForm } from '../CreateTontineForm';

vi.mock('../../../api/tontines', () => ({
  tontineApi: { create: vi.fn() },
}));

const createMock = vi.mocked(tontineApi.create);

describe('CreateTontineForm', () => {
  beforeEach(() => {
    createMock.mockReset();
  });

  it('affiche les champs du formulaire, places comprises', () => {
    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    expect(screen.getByLabelText('Nom')).toBeInTheDocument();
    expect(screen.getByLabelText(/cotisation par tour/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/mois de début/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/nombre de places/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/cadence des tours/i)).toBeInTheDocument();
  });

  it('annonce la fin du cycle déduite des places et de la cadence', async () => {
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    await user.clear(screen.getByLabelText(/mois de début/i));
    await user.type(screen.getByLabelText(/mois de début/i), '2026-08');
    await user.clear(screen.getByLabelText(/nombre de places/i));
    await user.type(screen.getByLabelText(/nombre de places/i), '5');

    // 5 tours d'un mois à partir d'août : le cycle se termine fin décembre.
    expect(await screen.findByText(/5 tours d'un mois/i)).toBeInTheDocument();

    // La cadence change la durée du cycle sans toucher au nombre de places.
    await user.selectOptions(screen.getByLabelText(/cadence des tours/i), 'WEEKLY');
    expect(await screen.findByText(/5 tours de 7 jours/i)).toBeInTheDocument();
  });

  it('crée la tontine avec le premier jour du mois choisi', async () => {
    createMock.mockResolvedValue(makeTontine());
    const onCreated = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={onCreated} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    await user.type(screen.getByLabelText('Nom'), 'Tontine Equipe A');
    await user.type(screen.getByLabelText(/cotisation par tour/i), '50000');
    await user.clear(screen.getByLabelText(/mois de début/i));
    await user.type(screen.getByLabelText(/mois de début/i), '2026-08');
    await user.click(screen.getByRole('button', { name: /créer la tontine/i }));

    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith({
        name: 'Tontine Equipe A',
        monthlyAmount: 50000,
        startDate: '2026-08-01',
        targetMemberCount: 5,
        frequency: 'MONTHLY',
        // Une cadence prédéfinie porte sa propre durée : rien à préciser.
        periodDays: null,
      }),
    );
    await waitFor(() => expect(onCreated).toHaveBeenCalled());
  });

  it('refuse un montant nul ou negatif sans appeler l API', async () => {
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    await user.type(screen.getByLabelText('Nom'), 'Tontine invalide');
    await user.type(screen.getByLabelText(/cotisation par tour/i), '0');
    await user.click(screen.getByRole('button', { name: /créer la tontine/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/strictement positif/i);
    expect(createMock).not.toHaveBeenCalled();
  });

  it('refuse un nom vide', async () => {
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    await user.type(screen.getByLabelText(/cotisation par tour/i), '50000');
    await user.click(screen.getByRole('button', { name: /créer la tontine/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/nom de la tontine est obligatoire/i);
    expect(createMock).not.toHaveBeenCalled();
  });

  it('affiche le message d erreur renvoye par le serveur', async () => {
    createMock.mockRejectedValue(new Error('refus'));
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    await user.type(screen.getByLabelText('Nom'), 'Tontine Equipe A');
    await user.type(screen.getByLabelText(/cotisation par tour/i), '50000');
    await user.click(screen.getByRole('button', { name: /créer la tontine/i }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('demande la durée du tour pour une cadence personnalisée', async () => {
    createMock.mockResolvedValue(makeTontine());
    const user = userEvent.setup();

    renderWithProviders(<CreateTontineForm onCreated={vi.fn()} />, {
      user: makeUser({ role: 'ACCOUNTANT' }),
    });

    // Le champ n'apparaît que si la durée ne découle pas de la cadence.
    expect(screen.queryByLabelText(/durée d'un tour/i)).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText(/cadence des tours/i), 'CUSTOM');

    const jours = await screen.findByLabelText(/durée d'un tour/i);
    await user.clear(jours);
    await user.type(jours, '2');

    await user.type(screen.getByLabelText('Nom'), 'Tontine Rapide');
    await user.type(screen.getByLabelText(/cotisation par tour/i), '5000');
    await user.click(screen.getByRole('button', { name: /créer la tontine/i }));

    await waitFor(() =>
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({ frequency: 'CUSTOM', periodDays: 2 }),
      ),
    );
  });
});
