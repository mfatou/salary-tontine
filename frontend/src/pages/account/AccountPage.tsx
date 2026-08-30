import { RoleBadge } from '../../components/RoleBadge';
import { Avatar } from '../../components/Avatar';
import { useAuth } from '../../hooks/useAuth';
import { formatDateTime } from '../../utils/format';
import { ROLE_DESCRIPTIONS } from '../../utils/labels';
import { ChangePasswordForm } from './ChangePasswordForm';
import { SalarySummary } from './SalarySummary';

/** Profil de l'utilisateur connecté et gestion de son mot de passe. */
export function AccountPage() {
  const { user } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Mon compte</h1>
          <p className="page-subtitle">Vos informations et la sécurité de votre accès.</p>
        </div>
      </header>

      <section className="card">
        <div className="request-head">
          <span className="identity">
            <Avatar name={user.name} />
            <span className="identity-text">
              <span className="identity-name">{user.name}</span>
              <span className="identity-email">{user.email}</span>
            </span>
          </span>
          <RoleBadge role={user.role} />
        </div>

        <dl className="definition-grid" style={{ marginTop: 'var(--space-md)' }}>
          <div>
            <dt>Compte créé le</dt>
            <dd>{formatDateTime(user.createdAt)}</dd>
          </div>
        </dl>

        <p className="muted">{ROLE_DESCRIPTIONS[user.role]}</p>
        
      </section>

      <SalarySummary />

      <ChangePasswordForm />
    </div>
  );
}
