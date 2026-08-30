import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ContributionStatusBadge, TontineStatusBadge } from '../../components/StatusBadge';
import { Spinner } from '../../components/Spinner';
import { useApiResource } from '../../hooks/useApiResource';
import { useAuth } from '../../hooks/useAuth';
import type { Contribution, ScheduleEntry, TontineDetail } from '../../types';
import { formatAmount, formatApproximateAmount, formatDate } from '../../utils/format';
import { FREQUENCY_LABELS } from '../../utils/labels';
import { MemberManager } from './MemberManager';
import { MonthlyGenerationPanel } from './MonthlyGenerationPanel';
import { TontineRequestsPanel } from './TontineRequestsPanel';

export function TontineDetailPage() {
  const { id } = useParams<{ id: string }>();
  const tontineId = Number(id);
  const { user, hasRole } = useAuth();
  const navigate = useNavigate();
  const canManage = hasRole('ACCOUNTANT', 'ADMIN');

  /** Action de cloture en attente de confirmation. */
  type PendingAction = 'activate' | 'leave' | 'cancel' | 'delete';
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const detail = useApiResource<TontineDetail>(() => tontineApi.get(tontineId), [tontineId]);
  const schedule = useApiResource<ScheduleEntry[]>(() => tontineApi.schedule(tontineId), [tontineId]);
  const contributions = useApiResource<Contribution[]>(
    () => tontineApi.listContributions(tontineId),
    [tontineId],
  );

  const reloadAll = useCallback(async () => {
    await Promise.all([detail.reload(), schedule.reload(), contributions.reload()]);
  }, [detail, schedule, contributions]);

  const confirmPendingAction = async () => {
    if (!pendingAction) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      if (pendingAction === 'activate') {
        await tontineApi.activate(tontineId);
      } else if (pendingAction === 'leave') {
        await tontineApi.leave(tontineId);
      } else if (pendingAction === 'cancel') {
        await tontineApi.cancel(tontineId);
      } else {
        await tontineApi.remove(tontineId);
        navigate('/tontines', { replace: true });
        return;
      }
      await reloadAll();
    } catch (caught) {
      setActionError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
      setPendingAction(null);
    }
  };

  const CONFIRMATIONS: Record<PendingAction, { title: string; message: string; label: string }> = {
    activate: {
      title: 'Activer la tontine ?',
      message:
        "Une fois activée, la composition, le montant et l'ordre de passage ne pourront plus être "
        + 'modifies, et la tontine n acceptera plus aucune inscription.',
      label: 'Activer',
    },
    leave: {
      title: 'Quitter cette tontine ?',
      message:
        "Votre place sera liberee et les ordres de passage des autres participants seront "
        + 'reajustes. Vous pourrez redemander a la rejoindre tant qu elle reste ouverte.',
      label: 'Quitter',
    },
    cancel: {
      title: 'Annuler la tontine ?',
      message:
        'Le cycle s arrête definitivement : plus aucune cotisation ni salaire ne sera généré. '
        + "Les mois déjà générés restent dans l historique salarial.",
      label: 'Annuler la tontine',
    },
    delete: {
      title: 'Supprimer la tontine ?',
      message:
        "La tontine et les demandes d adhesion qui s y rattachent seront effacees definitivement. "
        + 'Cette tontine n a pas demarre : aucun historique salarial ne sera perdu.',
      label: 'Supprimer',
    },
  };

  if (detail.loading) {
    return <Spinner label="Chargement de la tontine..." />;
  }
  if (detail.error) {
    return (
      <div className="page">
        <Alert variant="error">{detail.error}</Alert>
        <Link to="/tontines" className="button button-ghost">
          Retour aux tontines
        </Link>
      </div>
    );
  }
  if (!detail.data) {
    return null;
  }

  const { tontine, members } = detail.data;
  const isDraft = tontine.status === 'DRAFT';
  const isActive = tontine.status === 'ACTIVE';
  const isMember = members.some((member) => member.userId === user?.id);
  const confirmation = pendingAction ? CONFIRMATIONS[pendingAction] : null;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <Link to="/tontines" className="back-link">
            ← Tontines
          </Link>
          <h1>{tontine.name}</h1>
          <p className="page-subtitle">
            Créée par {tontine.createdByName} · début le {formatDate(tontine.startDate)}
          </p>
        </div>
        <TontineStatusBadge status={tontine.status} />
      </header>

      {actionError && <Alert variant="error">{actionError}</Alert>}

      <section className="card">
        <dl className="definition-grid">
          <div>
            <dt>Cotisation par tour</dt>
            <dd>{formatAmount(tontine.monthlyAmount)}</dd>
          </div>
          <div>
            <dt>Cadence</dt>
            <dd>
              {FREQUENCY_LABELS[tontine.frequency]}
              {tontine.periodLengthInDays && (
                <span className="muted"> · {tontine.periodLengthInDays} j/tour</span>
              )}
            </dd>
          </div>
          <div>
            <dt>Coût mensuel moyen</dt>
            <dd>{formatApproximateAmount(tontine.monthlyCost)}</dd>
          </div>
          <div>
            <dt>Participants</dt>
            <dd>{tontine.memberCount}</dd>
          </div>
          <div>
            <dt>Cagnotte mensuelle</dt>
            <dd>{formatAmount(tontine.potAmount)}</dd>
          </div>
          <div>
            <dt>Places</dt>
            <dd>
              {tontine.targetMemberCount
                ? `${tontine.memberCount} / ${tontine.targetMemberCount}`
                : 'Non limitees'}
            </dd>
          </div>
          <div>
            <dt>Fin du cycle</dt>
            <dd>
              {formatDate(tontine.endDate)}
              {isDraft && tontine.endMonth && <span className="muted"> (prévisionnel)</span>}
            </dd>
          </div>
          <div>
            <dt>Statut</dt>
            <dd>
              <TontineStatusBadge status={tontine.status} />
            </dd>
          </div>
        </dl>

        <div className="page-actions">
          {canManage && isDraft && (
            <button
              type="button"
              className="button button-primary"
              onClick={() => setPendingAction('activate')}
            >
              Activer la tontine
            </button>
          )}

          {isMember && isDraft && (
            <button
              type="button"
              className="button button-ghost"
              onClick={() => setPendingAction('leave')}
            >
              Quitter cette tontine
            </button>
          )}

          {canManage && (isDraft || isActive) && (
            <button
              type="button"
              className="button button-ghost"
              onClick={() => setPendingAction('cancel')}
            >
              Annuler la tontine
            </button>
          )}

          {canManage && isDraft && (
            <button
              type="button"
              className="button button-danger"
              onClick={() => setPendingAction('delete')}
            >
              Supprimer
            </button>
          )}
        </div>

        {isMember && isActive && (
          <p className="muted" style={{ marginTop: 'var(--space-sm)' }}>
            Le cycle a demarre : il ne peut plus être quitte. Chacun doit ses cotisations jusqu au
            dernier tour, sans quoi ceux qui ont déjà encaisse partiraient gagnants.
          </p>
        )}
      </section>

      <MemberManager
        tontineId={tontineId}
        members={members}
        editable={canManage && isDraft}
        onChanged={reloadAll}
      />

      {canManage && (
        <TontineRequestsPanel
          tontineId={tontineId}
          openForEnrollment={isDraft}
          onAccepted={reloadAll}
        />
      )}

      {canManage && isActive && (
        <MonthlyGenerationPanel
          tontineId={tontineId}
          schedule={schedule.data ?? []}
          onGenerated={reloadAll}
        />
      )}

      <section className="card">
        <h2 className="card-title">Calendrier prévisionnel</h2>
        {schedule.error && <Alert variant="error">{schedule.error}</Alert>}
        {schedule.data && schedule.data.length === 0 && (
          <p className="muted">Ajoutez des participants pour construire le calendrier.</p>
        )}
        {schedule.data && schedule.data.length > 0 && (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Tour</th>
                  <th scope="col">Période</th>
                  <th scope="col">Bénéficiaire</th>
                  <th scope="col">Cagnotte</th>
                </tr>
              </thead>
              <tbody>
                {schedule.data.map((entry) => (
                  <tr key={entry.periodIndex}>
                    <td data-label="Tour">{entry.turnOrder}</td>
                    <td data-label="Période">
                      du {formatDate(entry.start)} au {formatDate(entry.end)}
                    </td>
                    <td data-label="Bénéficiaire">{entry.beneficiaryName}</td>
                    <td data-label="Cagnotte">{formatAmount(tontine.potAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card">
        <h2 className="card-title">Cotisations</h2>
        {contributions.error && <Alert variant="error">{contributions.error}</Alert>}
        {contributions.data && contributions.data.length === 0 && (
          <p className="muted">Aucune cotisation générée pour le moment.</p>
        )}
        {contributions.data && contributions.data.length > 0 && (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Tour</th>
                  <th scope="col">Participant</th>
                  <th scope="col">Montant</th>
                  <th scope="col">Statut</th>
                </tr>
              </thead>
              <tbody>
                {contributions.data.map((contribution) => (
                  <tr key={contribution.id}>
                    <td data-label="Tour">
                      Tour {contribution.periodIndex} · {formatDate(contribution.periodStart)}
                    </td>
                    <td data-label="Participant">{contribution.userName}</td>
                    <td data-label="Montant">{formatAmount(contribution.amount)}</td>
                    <td data-label="Statut">
                      <ContributionStatusBadge status={contribution.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <ConfirmDialog
        open={confirmation !== null}
        title={confirmation?.title ?? ''}
        message={confirmation?.message ?? ''}
        confirmLabel={confirmation?.label ?? 'Confirmer'}
        busy={busy}
        onConfirm={confirmPendingAction}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
}
