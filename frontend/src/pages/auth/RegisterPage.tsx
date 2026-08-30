import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { extractErrorMessage } from '../../api/client';
import { Alert } from '../../components/Alert';
import { useAuth } from '../../hooks/useAuth';
import { AuthLayout } from '../../layouts/AuthLayout';

const MINIMUM_PASSWORD_LENGTH = 8;

export function RegisterPage() {
  const { user, register } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  /** Validation de confort : le serveur revalide systematiquement les mêmes règles. */
  const validate = (): string | null => {
    if (!name.trim() || !email.trim() || !password) {
      return 'Tous les champs sont obligatoires.';
    }
    if (password.length < MINIMUM_PASSWORD_LENGTH) {
      return `Le mot de passe doit contenir au moins ${MINIMUM_PASSWORD_LENGTH} caracteres.`;
    }
    if (password !== confirmPassword) {
      return 'Les deux mots de passe ne correspondent pas.';
    }
    return null;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    try {
      await register({ name: name.trim(), email: email.trim(), password });

      // Pas de connexion automatique : le compte n'est pas encore validé, la
      // tentative échouerait et laisserait croire que l'inscription a raté.
      setSubmitted(true);
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <AuthLayout
        title="Inscription enregistrée"
        subtitle="Votre compte doit maintenant être validé."
        footer={
          <p>
            <Link to="/login">Revenir à la connexion</Link>
          </p>
        }
      >
        <Alert variant="success">
          Merci {name.trim()}, votre inscription est enregistrée.
        </Alert>
        <p className="muted">
          Un administrateur doit valider votre compte avant que vous puissiez vous connecter.
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Créer un compte"
      subtitle="Choisissez votre mot de passe : il ne sera connu que de vous. Un administrateur validera ensuite votre inscription."
      footer={
        <p>
          Déjà inscrit ? <Link to="/login">Se connecter</Link>
        </p>
      }
    >
      <form onSubmit={handleSubmit} noValidate>
        {error && <Alert variant="error">{error}</Alert>}

        <div className="field">
          <label htmlFor="name">Nom complet</label>
          <input
            id="name"
            name="name"
            type="text"
            autoComplete="name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Awa Ndiaye"
          />
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="awa@example.test"
          />
        </div>

        <div className="field">
          <label htmlFor="password">Mot de passe</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <p className="field-hint">Au moins {MINIMUM_PASSWORD_LENGTH} caracteres.</p>
        </div>

        <div className="field">
          <label htmlFor="confirmPassword">Confirmation du mot de passe</label>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
        </div>

        <button type="submit" className="button button-primary button-block" disabled={submitting}>
          {submitting ? 'Création...' : 'Créer mon compte'}
        </button>
      </form>
    </AuthLayout>
  );
}
