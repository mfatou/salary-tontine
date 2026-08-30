import { useEffect, useMemo, useState } from 'react';
import { employeeApi } from '../../api/employees';
import { Alert } from '../../components/Alert';
import { Spinner } from '../../components/Spinner';
import { StatCard } from '../../components/StatCard';
import { useApiResource } from '../../hooks/useApiResource';
import type { User } from '../../types';
import { formatAmount } from '../../utils/format';
import { SalaryRow } from './SalaryRow';

/**
 * Annuaire salarial du comptable : c'est lui qui prepare les prélèvements,
 * il lui faut donc le salaire de base de chacun.
 */
export function EmployeesPage() {
  const { data, loading, error } = useApiResource<User[]>(() => employeeApi.list(), []);

  const [users, setUsers] = useState<User[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (data) {
      setUsers(data);
    }
  }, [data]);

  const handleUpdated = (updated: User) => {
    setUsers((current) => current.map((user) => (user.id === updated.id ? updated : user)));
    setActionError(null);
  };

  // Le comptable cotise comme les autres ; l'administrateur n'est pas salarié.
  const employees = useMemo(() => users.filter((user) => user.role !== 'ADMIN'), [users]);

  const payroll = useMemo(
    () => employees.reduce((total, user) => total + user.baseSalary, 0),
    [employees],
  );

  const withoutSalary = useMemo(
    () => employees.filter((user) => user.baseSalary <= 0).length,
    [employees],
  );

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) {
      return users;
    }
    return users.filter(
      (user) =>
        user.name.toLowerCase().includes(needle) || user.email.toLowerCase().includes(needle),
    );
  }, [users, search]);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Employés et salaires</h1>
          <p className="page-subtitle">
            Salaires de base fictifs servant de référence aux prélèvements de tontine.
          </p>
        </div>
      </header>

      <section className="stat-grid" aria-label="Synthese">
        <StatCard label="Salariés" value={String(employees.length)} hint="Comptable inclus" />
        <StatCard
          label="Masse salariale"
          value={formatAmount(payroll)}
          hint="Somme des salaires de base"
        />
        <StatCard
          label="Salaire non renseigne"
          value={String(withoutSalary)}
          variant={withoutSalary > 0 ? 'negative' : 'default'}
          hint={
            withoutSalary > 0
              ? 'Bloque l activation d une tontine'
              : 'Tous les employés sont paramètres'
          }
        />
      </section>

      {actionError && <Alert variant="error">{actionError}</Alert>}
      {loading && <Spinner label="Chargement de l annuaire..." />}
      {error && <Alert variant="error">{error}</Alert>}

      {users.length > 0 && (
        <section>
          <div className="field" style={{ maxWidth: '320px' }}>
            <label htmlFor="employee-search">Rechercher</label>
            <input
              id="employee-search"
              type="search"
              placeholder="Nom ou email"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>

          {visible.length === 0 ? (
            <p className="muted">Aucun resultat pour « {search} ».</p>
          ) : (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Employé</th>
                    <th scope="col">Role</th>
                    <th scope="col">Salaire de base</th>
                    <th scope="col">Modifier</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map((user) => (
                    <SalaryRow
                      key={user.id}
                      user={user}
                      onUpdated={handleUpdated}
                      onError={setActionError}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
