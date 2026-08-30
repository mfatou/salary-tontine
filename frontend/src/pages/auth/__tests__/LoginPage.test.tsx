import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../../test/renderWithProviders';
import { LoginPage } from '../LoginPage';

describe('LoginPage', () => {
  it('affiche le formulaire de connexion', () => {
    renderWithProviders(<LoginPage />);

    expect(screen.getByRole('heading', { name: /connexion/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/mot de passe/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /se connecter/i })).toBeInTheDocument();
  });

  it('propose un lien vers la création de compte', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByRole('link', { name: /créer un compte/i })).toBeInTheDocument();
  });

  it('refuse la soumission si les champs sont vides', async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<LoginPage />);

    await user.click(screen.getByRole('button', { name: /se connecter/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/email et votre mot de passe/i);
    expect(authValue.login).not.toHaveBeenCalled();
  });

  it('appelle login avec les identifiants saisis', async () => {
    const user = userEvent.setup();
    const { authValue } = renderWithProviders(<LoginPage />);

    await user.type(screen.getByLabelText(/email/i), 'awa@salarytontine.test');
    await user.type(screen.getByLabelText(/mot de passe/i), 'MotDePasse123');
    await user.click(screen.getByRole('button', { name: /se connecter/i }));

    await waitFor(() => {
      expect(authValue.login).toHaveBeenCalledWith({
        email: 'awa@salarytontine.test',
        password: 'MotDePasse123',
      });
    });
  });

  it('ne demande ni role ni salaire', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/salaire/i)).not.toBeInTheDocument();
  });
});
