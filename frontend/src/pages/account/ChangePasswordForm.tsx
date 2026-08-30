import { useState, type FormEvent } from 'react';
import { accountApi } from '../../api/account';
import { extractErrorMessage } from '../../api/client';
import { Alert } from '../../components/Alert';

const MINIMUM_LENGTH = 8;

/**
 * Changement de mot de passe par son propriétaire.
 *
 * <p>Le mot de passe actuel est exigé par le serveur : un jeton volé ne suffit
 * pas à verrouiller le compte de sa victime.</p>
 */
export function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const validate = (): string | null => {
    if (!currentPassword) {
      return 'Le mot de passe actuel est obligatoire.';
    }
    if (newPassword.length < MINIMUM_LENGTH) {
      return `Le nouveau mot de passe doit contenir au moins ${MINIMUM_LENGTH} caractères.`;
    }
    if (newPassword !== confirmation) {
      return 'Les deux nouveaux mots de passe ne correspondent pas.';
    }
    if (newPassword === currentPassword) {
      return 'Le nouveau mot de passe doit différer de l’actuel.';
    }
    return null;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    try {
      await accountApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setSuccess('Votre mot de passe a été modifié.');
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="card" onSubmit={handleSubmit} noValidate aria-label="Changer de mot de passe">
      <h2 className="card-title">Changer de mot de passe</h2>
      

      {error && <Alert variant="error">{error}</Alert>}
      {success && <Alert variant="success">{success}</Alert>}

      <div className="form-row" style={{ marginTop: 'var(--space-md)' }}>
        <div className="field">
          <label htmlFor="current-password">Mot de passe actuel</label>
          <input
            id="current-password"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="new-password">Nouveau mot de passe</label>
          <input
            id="new-password"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <p className="field-hint">{MINIMUM_LENGTH} caractères minimum.</p>
        </div>

        <div className="field">
          <label htmlFor="confirm-password">Confirmer le nouveau mot de passe</label>
          <input
            id="confirm-password"
            type="password"
            autoComplete="new-password"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </div>
      </div>

      <button type="submit" className="button button-primary" disabled={submitting}>
        {submitting ? 'Enregistrement...' : 'Modifier le mot de passe'}
      </button>
    </form>
  );
}
