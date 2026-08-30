import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { RegisterPage } from '../RegisterPage';

describe('RegisterPage', () => {
  it('affiche les quatre champs attendus', () => {
    renderWithProviders(<RegisterPage />);

    expect(screen.getByLabelText(/nom complet/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Mot de passe')).toBeInTheDocument();
    expect(screen.getByLabelText(/confirmation du mot de passe/i)).toBeInTheDocument();
  });

  it('ne propose jamais de choisir un role ou un salaire', () => {
    renderWithProviders(<RegisterPage />);

    expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/salaire/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('refusé deux mots de passe differents', async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<RegisterPage />);

    await user.type(screen.getByLabelText(/nom complet/i), 'Awa Ndiaye');
    await user.type(screen.getByLabelText(/email/i), 'awa@example.test');
    await user.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await user.type(screen.getByLabelText(/confirmation du mot de passe/i), 'AutreMotDePasse');
    await user.click(screen.getByRole('button', { name: /créer mon compte/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/ne correspondent pas/i);
    expect(authValue.register).not.toHaveBeenCalled();
  });

  it('refuse un mot de passe trop court', async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<RegisterPage />);

    await user.type(screen.getByLabelText(/nom complet/i), 'Awa Ndiaye');
    await user.type(screen.getByLabelText(/email/i), 'awa@example.test');
    await user.type(screen.getByLabelText('Mot de passe'), 'court');
    await user.type(screen.getByLabelText(/confirmation du mot de passe/i), 'court');
    await user.click(screen.getByRole('button', { name: /créer mon compte/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/au moins 8 caracteres/i);
    expect(authValue.register).not.toHaveBeenCalled();
  });

  it('soumet une inscription valide', async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<RegisterPage />);

    await user.type(screen.getByLabelText(/nom complet/i), 'Awa Ndiaye');
    await user.type(screen.getByLabelText(/email/i), 'awa@example.test');
    await user.type(screen.getByLabelText('Mot de passe'), 'MotDePasse123');
    await user.type(screen.getByLabelText(/confirmation du mot de passe/i), 'MotDePasse123');
    await user.click(screen.getByRole('button', { name: /créer mon compte/i }));

    await waitFor(() => {
      expect(authValue.register).toHaveBeenCalledWith({
        name: 'Awa Ndiaye',
        email: 'awa@example.test',
        password: 'MotDePasse123',
      });
    });
  });

  it("annonce l'attente de validation au lieu de connecter l'utilisateur", async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<RegisterPage />, { user: null });

    await user.type(screen.getByLabelText(/nom complet/i), 'Ndeye Diagne');
    await user.type(screen.getByLabelText(/^email$/i), 'ndeye@salarytontine.test');
    await user.type(screen.getByLabelText(/^mot de passe$/i), 'MotDePasse123');
    await user.type(screen.getByLabelText(/confirmation du mot de passe/i), 'MotDePasse123');
    await user.click(screen.getByRole('button', { name: /créer/i }));

    expect(await screen.findByText(/inscription est enregistrée/i)).toBeInTheDocument();
    // Connecter d'office échouerait : le compte n'est pas encore validé.
    expect(authValue.login).not.toHaveBeenCalled();
  });
});
