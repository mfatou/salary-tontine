import { useEffect, useMemo, useState } from 'react';
import { adminApi } from '../../api/admin';
import { extractErrorMessage } from '../../api/client';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Identity } from '../../components/Identity';
import { RoleBadge } from '../../components/RoleBadge';
import { Spinner } from '../../components/Spinner';
import { UserStatusBadge } from '../../components/StatusBadgeUser';
import { useApiResource } from '../../hooks/useApiResource';
import type { Role, User } from '../../types';
import { formatDateTime } from '../../utils/format';
import { ROLE_LABELS } from '../../utils/labels';
import { PendingUserCard } from './PendingUserCard';

/**
 * Inscriptions à valider.
 *
 * <p>L'administrateur attribue un rôle et ouvre l'accès. Il ne voit ni ne
 * choisit le mot de passe : celui-ci a été défini par l'employé et n'est connu
 * que de lui.</p>
 */
export function RegistrationsPage() {
  const usersResource = useApiResource<User[]>(() => adminApi.listUsers(), []);

  const [users, setUsers] = useState<User[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    if (usersResource.data) {
      setUsers(usersResource.data);
    }
  }, [usersResource.data]);

  const decide = async (
    userId: number,
    action: () => Promise<User>,
    message: (user: User) => string,
  ) => {
    setBusyId(userId);
    setActionError(null);
    setNotice(null);
    try {
      const updated = await action();
      setUsers((current) => current.map((user) => (user.id === updated.id ? updated : user)));
      setNotice(message(updated));
    } catch (caught) {
      setActionError(extractErrorMessage(caught));
    } finally {
      setBusyId(null);
    }
  };

  const handleApprove = (userId: number, role: Role, baseSalary: number | null) =>
    decide(
      userId,
      () => adminApi.approveUser(userId, { role, baseSalary }),
      (user) => `Le compte de ${user.name} est validé, avec le rôle ${ROLE_LABELS[user.role]}.`,
    );

  const handleReject = (userId: number) =>
    decide(
      userId,
      () => adminApi.rejectUser(userId),
      (user) => `L'inscription de ${user.name} a été refusée.`,
    );

  const pending = useMemo(() => users.filter((user) => user.status === 'PENDING'), [users]);

  // Les décisions récentes restent visibles : sans cela, valider ferait
  // disparaître la ligne sans laisser de trace de ce qui vient d'être fait.
  const recentlyDecided = useMemo(
    () => users.filter((user) => user.status === 'REJECTED'),
    [users],
  );

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Inscriptions</h1>
          <p className="page-subtitle">
            Les employés s'inscrivent eux-mêmes et choisissent leur mot de passe. Vous attribuez
            le rôle et ouvrez l'accès, sans jamais connaître leur mot de passe.
          </p>
        </div>
      </header>

      {notice && <Alert variant="success">{notice}</Alert>}
      {actionError && <Alert variant="error">{actionError}</Alert>}
      {usersResource.loading && <Spinner label="Chargement des inscriptions..." />}
      {usersResource.error && <Alert variant="error">{usersResource.error}</Alert>}

      <section aria-labelledby="pending-title">
        <div className="section-heading">
          <h2 id="pending-title" className="section-title">
            En attente de validation
          </h2>
          {pending.length > 0 && <span className="badge badge-pending">{pending.length}</span>}
        </div>

        {!usersResource.loading && pending.length === 0 ? (
          <EmptyState
            title="Aucune inscription en attente"
            description="Les nouvelles inscriptions apparaîtront ici pour validation."
          />
        ) : (
          <div className="request-list">
            {pending.map((user) => (
              <PendingUserCard
                key={user.id}
                user={user}
                busy={busyId === user.id}
                onApprove={handleApprove}
                onReject={handleReject}
              />
            ))}
          </div>
        )}
      </section>

      {recentlyDecided.length > 0 && (
        <section aria-labelledby="rejected-title">
          <div className="section-heading">
            <h2 id="rejected-title" className="section-title">
              Inscriptions refusées
            </h2>
          </div>

          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Personne</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Rôle</th>
                  <th scope="col">Inscrite le</th>
                </tr>
              </thead>
              <tbody>
                {recentlyDecided.map((user) => (
                  <tr key={user.id}>
                    <td data-label="Personne">
                      <Identity name={user.name} email={user.email} />
                    </td>
                    <td data-label="Statut">
                      <UserStatusBadge status={user.status} />
                    </td>
                    <td data-label="Rôle">
                      <RoleBadge role={user.role} />
                    </td>
                    <td data-label="Inscrite le">{formatDateTime(user.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
