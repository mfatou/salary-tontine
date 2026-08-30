import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import { Alert } from '../../components/Alert';
import { Spinner } from '../../components/Spinner';
import { StatCard } from '../../components/StatCard';
import { useApiResource } from '../../hooks/useApiResource';
import type { Role, User } from '../../types';
import { ALL_ROLES, ROLE_LABELS } from '../../utils/labels';
import { UserRow } from './UserRow';

/**
 * Comptes existants et rôles.
 *
 * <p>Les inscriptions à valider vivent sur leur propre page : ce sont deux
 * gestes distincts, l'un ponctuel à l'arrivée d'un employé, l'autre continu
 * sur la population déjà en place.</p>
 */
export function UsersPage() {
  const usersResource = useApiResource<User[]>(() => adminApi.listUsers(), []);

  const [users, setUsers] = useState<User[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [roleFilter, setRoleFilter] = useState<Role | 'ALL'>('ALL');

  useEffect(() => {
    if (usersResource.data) {
      setUsers(usersResource.data);
    }
  }, [usersResource.data]);

  const replaceUser = (updated: User) =>
    setUsers((current) => current.map((user) => (user.id === updated.id ? updated : user)));

  const handleUserUpdated = (updated: User) => {
    replaceUser(updated);
    setActionError(null);
  };

  // Les comptes encore en attente relèvent de la page Inscriptions.
  const decided = useMemo(() => users.filter((user) => user.status !== 'PENDING'), [users]);
  const pendingCount = useMemo(
    () => users.filter((user) => user.status === 'PENDING').length,
    [users],
  );

  const countByRole = useMemo(() => {
    const counts: Record<Role, number> = { EMPLOYEE: 0, ACCOUNTANT: 0, ADMIN: 0 };
    decided.filter((user) => user.status === 'ACTIVE').forEach((user) => {
      counts[user.role] += 1;
    });
    return counts;
  }, [decided]);

  const visible = useMemo(
    () => (roleFilter === 'ALL' ? decided : decided.filter((user) => user.role === roleFilter)),
    [decided, roleFilter],
  );

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Comptes</h1>
          <p className="page-subtitle">
            Rôles et salaires des comptes déjà ouverts. Les nouvelles inscriptions se valident
            depuis la page Inscriptions.
          </p>
        </div>
      </header>

      <section className="stat-grid" aria-label="Répartition des rôles">
        {ALL_ROLES.map((role) => (
          <StatCard key={role} label={ROLE_LABELS[role]} value={String(countByRole[role])} />
        ))}
      </section>

      {pendingCount > 0 && (
        <Alert variant="info">
          {pendingCount === 1
            ? 'Une inscription attend votre validation.'
            : `${pendingCount} inscriptions attendent votre validation.`}{' '}
          <Link to="/inscriptions">Les traiter</Link>
        </Alert>
      )}

      {actionError && <Alert variant="error">{actionError}</Alert>}
      {usersResource.loading && <Spinner label="Chargement des comptes..." />}
      {usersResource.error && <Alert variant="error">{usersResource.error}</Alert>}

      {decided.length > 0 && (
        <section aria-labelledby="accounts-title">
          <div className="section-heading">
            <h2 id="accounts-title" className="section-title">
              Comptes ouverts
            </h2>
          </div>

          <div className="field" style={{ maxWidth: '260px' }}>
            <label htmlFor="user-role-filter">Filtrer par rôle</label>
            <select
              id="user-role-filter"
              value={roleFilter}
              onChange={(event) => setRoleFilter(event.target.value as Role | 'ALL')}
            >
              <option value="ALL">Tous les rôles</option>
              {ALL_ROLES.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABELS[role]}
                </option>
              ))}
            </select>
          </div>

          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Nom</th>
                  <th scope="col">Email</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Rôle</th>
                  <th scope="col">Salaire fictif</th>
                  <th scope="col">Modifier le salaire</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((user) => (
                  <UserRow
                    key={user.id}
                    user={user}
                    onUpdated={handleUserUpdated}
                    onError={setActionError}
                  />
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
