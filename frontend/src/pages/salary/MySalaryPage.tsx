import { salaryApi } from '../../api/salaries';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Spinner } from '../../components/Spinner';
import { StatCard } from '../../components/StatCard';
import { useApiResource } from '../../hooks/useApiResource';
import { useAuth } from '../../hooks/useAuth';
import type { SalaryRecord } from '../../types';
import { formatAmount, formatMonth, formatSignedAmount } from '../../utils/format';

export function MySalaryPage() {
  const { user } = useAuth();
  const { data, loading, error } = useApiResource<SalaryRecord[]>(() => salaryApi.mine(), []);

  const records = data ?? [];
  const latest = records[0];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Mon salaire simule</h1>
          <p className="page-subtitle">
            Historique de l'impact de la tontine sur votre salaire fictif.
          </p>
        </div>
      </header>

      <section aria-labelledby="current-salary-title">
        <h2 id="current-salary-title" className="section-title">
          Situation actuelle
        </h2>
        <div className="stat-grid">
          <StatCard label="Salaire de base" value={formatAmount(user?.baseSalary ?? 0)} />
          {latest && (
            <>
              <StatCard
                label={`Cotisation ${formatMonth(latest.salaryMonth)}`}
                value={formatSignedAmount(latest.tontineDeduction, '-')}
                variant="negative"
              />
              <StatCard
                label="Tontine reçue"
                value={formatSignedAmount(latest.tontineReceived, '+')}
                variant={latest.beneficiary ? 'positive' : 'default'}
              />
              <StatCard
                label="Salaire simule"
                value={formatAmount(latest.finalSalary)}
                variant="highlight"
              />
            </>
          )}
        </div>
      </section>

      <section aria-labelledby="salary-history-title">
        <h2 id="salary-history-title" className="section-title">
          Historique mensuel
        </h2>

        {loading && <Spinner label="Chargement de l'historique..." />}
        {error && <Alert variant="error">{error}</Alert>}

        {!loading && !error && records.length === 0 && (
          <EmptyState
            title="Aucun salaire simule enregistre"
            description="Les salaires apparaitront ici une fois qu'un gestionnaire aura généré le mois."
          />
        )}

        {records.length > 0 && (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Mois</th>
                  <th scope="col">Tontine</th>
                  <th scope="col">Salaire de base</th>
                  <th scope="col">Cotisation</th>
                  <th scope="col">Tontine reçue</th>
                  <th scope="col">Salaire simule</th>
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr key={record.id} className={record.beneficiary ? 'row-highlight' : undefined}>
                    <td data-label="Mois">{formatMonth(record.salaryMonth)}</td>
                    <td data-label="Tontine">{record.tontineName}</td>
                    <td data-label="Salaire de base">{formatAmount(record.baseSalary)}</td>
                    <td data-label="Cotisation" className="amount-negative">
                      {formatSignedAmount(record.tontineDeduction, '-')}
                    </td>
                    <td data-label="Tontine reçue" className={record.beneficiary ? 'amount-positive' : undefined}>
                      {formatSignedAmount(record.tontineReceived, '+')}
                    </td>
                    <td data-label="Salaire simule">
                      <strong>{formatAmount(record.finalSalary)}</strong>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
