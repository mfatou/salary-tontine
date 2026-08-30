import { Link } from 'react-router-dom';
import { dashboardApi } from '../../api/salaries';
import { employeeApi } from '../../api/employees';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Spinner } from '../../components/Spinner';
import { StatCard } from '../../components/StatCard';
import { TontineStatusBadge } from '../../components/StatusBadge';
import { useApiResource } from '../../hooks/useApiResource';
import { useAuth } from '../../hooks/useAuth';
import type { Dashboard, JoinRequest, ScheduleEntry, Tontine, User } from '../../types';
import { formatAmount, formatDate, formatMonth, formatSignedAmount } from '../../utils/format';
import { JOIN_REQUEST_STATUS_LABELS } from '../../utils/labels';

const EMPTY_LIST = Promise.resolve([]);

export function DashboardPage() {
  const { user, hasRole } = useAuth();
  const canManage = hasRole('ACCOUNTANT', 'ADMIN');

  const dashboard = useApiResource<Dashboard>(() => dashboardApi.load(), []);
  const activeTontineId = dashboard.data?.activeTontine?.id ?? null;

  // Les ressources sans objet pour le role courant se resolvent en liste vide :
  // les hooks restent appeles a chaque rendu, comme React l'exige.
  const schedule = useApiResource<ScheduleEntry[]>(
    () => (activeTontineId ? tontineApi.schedule(activeTontineId) : EMPTY_LIST),
    [activeTontineId],
  );
  const pendingRequests = useApiResource<JoinRequest[]>(
    () => (canManage ? tontineApi.pendingJoinRequests() : EMPTY_LIST),
    [canManage],
  );
  const myRequests = useApiResource<JoinRequest[]>(
    () => (canManage ? EMPTY_LIST : tontineApi.myJoinRequests()),
    [canManage],
  );
  const allTontines = useApiResource<Tontine[]>(
    () => (canManage ? tontineApi.list() : EMPTY_LIST),
    [canManage],
  );
  const employees = useApiResource<User[]>(
    () => (canManage ? employeeApi.list() : EMPTY_LIST),
    [canManage],
  );

  if (dashboard.loading) {
    return <Spinner label="Chargement du tableau de bord..." />;
  }
  if (dashboard.error) {
    return <Alert variant="error">{dashboard.error}</Alert>;
  }
  if (!dashboard.data) {
    return null;
  }

  const {
    activeTontine,
    latestSalaryRecord,
    myTurnOrder,
    myTurnDate,
    nextBeneficiary,
    activeTontineCount,
  } = dashboard.data;

  const pendingCount = pendingRequests.data?.length ?? 0;
  const tontines = allTontines.data ?? [];
  const activeCount = tontines.filter((tontine) => tontine.status === 'ACTIVE').length;
  const draftCount = tontines.filter((tontine) => tontine.status === 'DRAFT').length;
  const missingSalary = (employees.data ?? []).filter(
    (person) => person.role === 'EMPLOYEE' && person.baseSalary <= 0,
  ).length;

  const openRequests = (myRequests.data ?? []).filter(
    (request) => request.status !== 'ACCEPTED',
  );
  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Bonjour {user?.name}</h1>
          <p className="page-subtitle">
            {canManage
              ? 'Etat des tontines et des demandes en attente d arbitrage.'
              : 'Voici la simulation de votre situation salariale.'}
          </p>
        </div>
      </header>

      {canManage && (
        <section aria-labelledby="management-title">
          <h2 id="management-title" className="section-title">
            Pilotage
          </h2>
          <div className="stat-grid">
            <StatCard
              label="Demandes en attente"
              value={String(pendingCount)}
              variant={pendingCount > 0 ? 'highlight' : 'default'}
              hint={pendingCount > 0 ? 'A arbitrer' : 'Rien a traiter'}
            />
            <StatCard label="Tontines actives" value={String(activeCount)} />
            <StatCard
              label="Tontines ouvertes"
              value={String(draftCount)}
              hint="Acceptent encore des inscriptions"
            />
            <StatCard
              label="Salaires manquants"
              value={String(missingSalary)}
              variant={missingSalary > 0 ? 'negative' : 'default'}
              hint={missingSalary > 0 ? 'Bloque l activation' : 'Tout est renseigne'}
            />
          </div>

          <div className="page-actions" style={{ marginTop: 'var(--space-sm)' }}>
            <Link to="/demandes" className="button button-primary button-small">
              Traiter les demandes
            </Link>
            <Link to="/employés" className="button button-ghost button-small">
              Annuaire salarial
            </Link>
            <Link to="/tontines" className="button button-ghost button-small">
              Gerer les tontines
            </Link>
          </div>
        </section>
      )}

      {!canManage && (
        <section aria-labelledby="salary-summary-title">
          <h2 id="salary-summary-title" className="section-title">
            {latestSalaryRecord
              ? `Simulation de ${formatMonth(latestSalaryRecord.salaryMonth)}`
              : 'Salaire de base'}
          </h2>

          <div className="stat-grid">
            <StatCard label="Salaire de base" value={formatAmount(dashboard.data.baseSalary)} />

            {latestSalaryRecord ? (
              <>
                <StatCard
                  label="Cotisation du mois"
                  value={formatSignedAmount(latestSalaryRecord.tontineDeduction, '-')}
                  variant="negative"
                />
                <StatCard
                  label="Tontine reçue"
                  value={formatSignedAmount(latestSalaryRecord.tontineReceived, '+')}
                  variant={latestSalaryRecord.beneficiary ? 'positive' : 'default'}
                  hint={
                    latestSalaryRecord.beneficiary ? 'Vous etes bénéficiaire ce mois-ci' : undefined
                  }
                />
                <StatCard
                  label="Salaire simule"
                  value={formatAmount(latestSalaryRecord.finalSalary)}
                  variant="highlight"
                  hint={
                    activeTontineCount > 1 ? 'Toutes tontines confondues' : undefined
                  }
                />
              </>
            ) : (
              <StatCard
                label="Salaire simule"
                value={formatAmount(dashboard.data.baseSalary)}
                hint="Aucune cotisation enregistree"
              />
            )}
          </div>
        </section>
      )}

      <section aria-labelledby="tontine-summary-title">
        <div className="section-heading">
          <h2 id="tontine-summary-title" className="section-title">
            {canManage ? 'Ma participation' : 'Ma tontine active'}
          </h2>
          {activeTontineCount > 1 && (
            <Link to="/tontines" className="muted">
              {activeTontineCount} tontines actives — voir toutes
            </Link>
          )}
        </div>

        {activeTontine ? (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title">{activeTontine.name}</h3>
                <p className="card-subtitle">
                  {activeTontine.memberCount} participants · cotisation de{' '}
                  {formatAmount(activeTontine.monthlyAmount)} par mois · fin en{' '}
                  {formatMonth(activeTontine.endMonth)}
                </p>
              </div>
              <TontineStatusBadge status={activeTontine.status} />
            </div>

            <dl className="definition-grid">
              <div>
                <dt>Cagnotte mensuelle</dt>
                <dd>{formatAmount(activeTontine.potAmount)}</dd>
              </div>
              <div>
                <dt>Ma position</dt>
                <dd>{myTurnOrder ? `${myTurnOrder} / ${activeTontine.memberCount}` : '—'}</dd>
              </div>
              <div>
                <dt>Date de mon tour</dt>
                <dd>{formatDate(myTurnDate)}</dd>
              </div>
              <div>
                <dt>Prochain bénéficiaire</dt>
                <dd>
                  {nextBeneficiary
                    ? `${nextBeneficiary.beneficiaryName} (${formatDate(nextBeneficiary.start)})`
                    : 'Cycle terminé'}
                </dd>
              </div>
            </dl>

            {schedule.data && schedule.data.length > 0 && (
              <>
                <h4 className="section-title">Deroulement du cycle</h4>
                <ol className="cycle-track">
                  {schedule.data.map((entry) => {
                    const mine = entry.beneficiaryUserId === user?.id;
                    const past = entry.end < today;
                    return (
                      <li
                        key={entry.periodIndex}
                        className={`cycle-step ${mine ? 'cycle-step-mine' : ''} ${
                          past ? 'cycle-step-past' : ''
                        }`}
                      >
                        <span className="cycle-step-month">{formatDate(entry.start)}</span>
                        <span className="cycle-step-name">
                          {mine ? 'Vous' : entry.beneficiaryName}
                        </span>
                      </li>
                    );
                  })}
                </ol>
              </>
            )}

            <Link to={`/tontines/${activeTontine.id}`} className="button button-primary">
              Voir la tontine
            </Link>
          </div>
        ) : (
          <EmptyState
            title="Vous ne participez a aucune tontine active"
            description={
              canManage
                ? 'Vous gerez les tontines sans y participer.'
                : 'Parcourez les tontines ouvertes et demandez a en rejoindre une.'
            }
          >
            <Link to="/tontines" className="button button-primary">
              {canManage ? 'Voir les tontines' : 'Trouver une tontine'}
            </Link>
          </EmptyState>
        )}
      </section>

      {!canManage && openRequests.length > 0 && (
        <section aria-labelledby="my-requests-title">
          <h2 id="my-requests-title" className="section-title">
            Mes demandes
          </h2>
          <div className="card">
            <ul style={{ margin: 0, paddingLeft: '1.1rem' }}>
              {openRequests.map((request) => (
                <li key={request.id}>
                  <Link to={`/tontines/${request.tontineId}`}>{request.tontineName}</Link> —{' '}
                  <span className={`badge badge-${request.status.toLowerCase()}`}>
                    {JOIN_REQUEST_STATUS_LABELS[request.status]}
                  </span>
                  {request.decisionNote && <span className="muted"> · {request.decisionNote}</span>}
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}
    </div>
  );
}
