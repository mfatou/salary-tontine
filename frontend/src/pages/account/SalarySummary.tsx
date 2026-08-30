import { Link } from 'react-router-dom';
import { dashboardApi } from '../../api/salaries';
import { Alert } from '../../components/Alert';
import { Spinner } from '../../components/Spinner';
import { StatCard } from '../../components/StatCard';
import { useApiResource } from '../../hooks/useApiResource';
import type { Dashboard } from '../../types';
import { formatAmount, formatDate, formatMonth, formatSignedAmount } from '../../utils/format';
import { FREQUENCY_LABELS } from '../../utils/labels';

/**
 * Situation salariale de l'employé : ce qu'il gagne, ce qui lui est retenu, et
 * ce que son tour lui rapportera.
 *
 * <p>Tous les montants viennent du serveur, y compris la projection : le
 * frontend n'effectue aucun calcul monétaire.</p>
 */
export function SalarySummary() {
  const { data, loading, error } = useApiResource<Dashboard>(() => dashboardApi.load(), []);

  if (loading) {
    return <Spinner label="Chargement de votre situation salariale..." />;
  }
  if (error) {
    return <Alert variant="error">{error}</Alert>;
  }
  if (!data) {
    return null;
  }

  const {
    baseSalary,
    latestSalaryRecord,
    activeTontine,
    myTurnOrder,
    myTurnDate,
    myTurnPotAmount,
    projectedTurnSalary,
    activeTontineCount,
  } = data;

  const turnIsPast =
    myTurnDate !== null && myTurnDate < new Date().toISOString().slice(0, 10);

  return (
    <>
      <section className="card">
        <h2 className="card-title">Ma situation salariale</h2>
        <p className="card-subtitle">
          {latestSalaryRecord
            ? `Dernier bulletin simulé : ${formatMonth(latestSalaryRecord.salaryMonth)}.`
            : 'Aucun bulletin simulé pour le moment : votre salaire de base est intact.'}
        </p>

        <div className="stat-grid" style={{ marginTop: 'var(--space-md)' }}>
          <StatCard
            label="Salaire de base"
            value={formatAmount(baseSalary)}
            hint="Défini par l'administration"
          />

          {latestSalaryRecord ? (
            <>
              <StatCard
                label="Retenues du mois"
                value={formatSignedAmount(latestSalaryRecord.tontineDeduction, '-')}
                variant="negative"
                hint={activeTontineCount > 1 ? 'Toutes tontines confondues' : undefined}
              />
              <StatCard
                label="Cagnotte encaissée"
                value={formatSignedAmount(latestSalaryRecord.tontineReceived, '+')}
                hint={latestSalaryRecord.beneficiary ? 'C’était votre tour' : 'Pas votre tour'}
              />
              <StatCard
                label="Salaire actuel"
                value={formatAmount(latestSalaryRecord.finalSalary)}
                variant="highlight"
                hint="Après retenues"
              />
            </>
          ) : (
            <StatCard
              label="Salaire actuel"
              value={formatAmount(baseSalary)}
              variant="highlight"
              hint="Aucune retenue enregistrée"
            />
          )}
        </div>
      </section>

      {activeTontine && (
        <section className="card card-accent">
          <div className="card-header">
            <div>
              <h2 className="card-title">
                {turnIsPast ? 'Votre tour est passé' : 'Quand viendra votre tour'}
              </h2>
              <p className="card-subtitle">
                {activeTontine.name} · {FREQUENCY_LABELS[activeTontine.frequency]} · position{' '}
                {myTurnOrder} sur {activeTontine.memberCount}
              </p>
            </div>
          </div>

          <div className="stat-grid">
            <StatCard
              label={turnIsPast ? 'Tour encaissé le' : 'Date de votre tour'}
              value={formatDate(myTurnDate)}
            />
            <StatCard
              label="Cagnotte à encaisser"
              value={formatAmount(myTurnPotAmount)}
              hint={`${formatAmount(activeTontine.monthlyAmount)} × ${activeTontine.memberCount} participants`}
            />
            <StatCard
              label="Salaire ce mois-là"
              value={formatAmount(projectedTurnSalary)}
              variant="highlight"
              hint="Base − cotisations + cagnotte"
            />
          </div>

          <p className="muted">
            Ce montant est une projection : les valeurs définitives sont calculées au moment de
            la génération du bulletin.{' '}
            <Link to={`/tontines/${activeTontine.id}`}>Voir la tontine</Link>
          </p>
        </section>
      )}
    </>
  );
}
