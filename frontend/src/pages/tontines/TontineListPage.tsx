import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { TontineStatusBadge } from '../../components/StatusBadge';
import { useApiResource } from '../../hooks/useApiResource';
import { useAuth } from '../../hooks/useAuth';
import type { JoinRequest, Tontine } from '../../types';
import { formatAmount, formatMonth } from '../../utils/format';
import { FREQUENCY_LABELS } from '../../utils/labels';
import { CreateTontineForm } from './CreateTontineForm';
import { OpenTontineCard } from './OpenTontineCard';

export function TontineListPage() {
  const { hasRole } = useAuth();
  const canManage = hasRole('ACCOUNTANT', 'ADMIN');
  // Le comptable est un salarié comme les autres : il cotise. L'administrateur
  // gouverne l'application sans en être employé, il ne participe donc pas.
  const canJoin = !hasRole('ADMIN');
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const mine = useApiResource<Tontine[]>(() => tontineApi.list(), []);
  const open = useApiResource<Tontine[]>(() => tontineApi.listOpen(), []);
  const myRequests = useApiResource<JoinRequest[]>(() => tontineApi.myJoinRequests(), []);

  const reloadAll = useCallback(async () => {
    await Promise.all([mine.reload(), open.reload(), myRequests.reload()]);
  }, [mine, open, myRequests]);

  const handleCreated = async () => {
    setShowForm(false);
    setNotice('Tontine créée. Les employés peuvent desormais demander a la rejoindre.');
    await reloadAll();
  };

  const myTontineIds = useMemo(
    () => new Set((mine.data ?? []).map((tontine) => tontine.id)),
    [mine.data],
  );

  const requestByTontineId = useMemo(() => {
    const map = new Map<number, JoinRequest>();
    (myRequests.data ?? []).forEach((request) => map.set(request.tontineId, request));
    return map;
  }, [myRequests.data]);

  const joinable = useMemo(() => (canJoin ? open.data ?? [] : []), [canJoin, open.data]);

  const runAction = async (action: () => Promise<unknown>, successMessage: string) => {
    setBusy(true);
    setActionError(null);
    setNotice(null);
    try {
      await action();
      setNotice(successMessage);
      await reloadAll();
    } catch (caught) {
      setActionError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = (tontineId: number, motivation?: string) =>
    runAction(
      () => tontineApi.requestJoin(tontineId, { motivation }),
      'Demande envoyee. Le comptable doit maintenant la valider.',
    );

  const handleCancel = (tontineId: number) =>
    runAction(() => tontineApi.cancelJoinRequest(tontineId), 'Votre demande a été retiree.');

  const handleLeave = (tontineId: number) =>
    runAction(
      () => tontineApi.leave(tontineId),
      'Vous avez quitte la tontine. Les ordres de passage ont été reajustes.',
    );

  const tontines = mine.data ?? [];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Tontines</h1>
          <p className="page-subtitle">
            {canManage
              ? 'Toutes les tontines de l’entreprise.'
              : 'Vos tontines, et celles que vous pouvez rejoindre.'}
          </p>
        </div>
        {canManage && (
          <button
            type="button"
            className="button button-primary"
            onClick={() => setShowForm(true)}
          >
            Nouvelle tontine
          </button>
        )}
      </header>

      {actionError && <Alert variant="error">{actionError}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}



      <section aria-labelledby="my-tontines-title">
        <div className="section-heading">
          <h2 id="my-tontines-title" className="section-title">
            {canManage ? 'Toutes les tontines' : 'Mes tontines'}
          </h2>
        </div>

        {mine.loading && <Spinner label="Chargement des tontines..." />}
        {mine.error && <Alert variant="error">{mine.error}</Alert>}

        {!mine.loading && !mine.error && tontines.length === 0 && (
          <EmptyState
            title={canManage ? 'Aucune tontine' : 'Vous ne participez a aucune tontine'}
            description={
              canManage
                ? 'Creez une première tontine pour demarrer.'
                : 'Demandez a rejoindre une tontine ouverte ci-dessous.'
            }
          />
        )}

        {tontines.length > 0 && (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Nom</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Cotisation</th>
                  <th scope="col">Cadence</th>
                  <th scope="col">Participants</th>
                  <th scope="col">Cagnotte</th>
                  <th scope="col">Début</th>
                  <th scope="col">Fin de cycle</th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {tontines.map((tontine) => (
                  <tr key={tontine.id}>
                    <td data-label="Nom">{tontine.name}</td>
                    <td data-label="Statut">
                      <TontineStatusBadge status={tontine.status} />
                    </td>
                    <td data-label="Cotisation" className="table-numeric">
                      {formatAmount(tontine.monthlyAmount)}
                    </td>
                    <td data-label="Cadence">{FREQUENCY_LABELS[tontine.frequency]}</td>
                    <td data-label="Participants" className="table-numeric">
                      {tontine.targetMemberCount
                        ? `${tontine.memberCount} / ${tontine.targetMemberCount}`
                        : tontine.memberCount}
                    </td>
                    <td data-label="Cagnotte" className="table-numeric">
                      {formatAmount(tontine.potAmount)}
                    </td>
                    <td data-label="Début">{formatMonth(tontine.startMonth)}</td>
                    <td data-label="Fin de cycle">
                      {formatMonth(tontine.endMonth)}
                      {tontine.status === 'DRAFT' && tontine.endMonth && (
                        <span className="muted"> (prev.)</span>
                      )}
                    </td>
                    <td data-label="Actions">
                      <Link
                        to={`/tontines/${tontine.id}`}
                        className="button button-ghost button-small"
                      >
                        Détails
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {canJoin && (
        <section aria-labelledby="open-tontines-title">
          <div className="section-heading">
            <h2 id="open-tontines-title" className="section-title">
              Tontines ouvertes aux inscriptions
            </h2>
          </div>

          {open.loading && <Spinner label="Chargement des tontines ouvertes..." />}
          {open.error && <Alert variant="error">{open.error}</Alert>}

          {!open.loading && !open.error && joinable.length === 0 && (
            <EmptyState
              title="Aucune tontine ouverte"
              description="Une tontine n'accepte de nouvelles inscriptions que tant qu'elle n'est pas activée."
            />
          )}

          {joinable.length > 0 && (
            <div className="split-grid">
              {joinable.map((tontine) => (
                <OpenTontineCard
                  key={tontine.id}
                  tontine={tontine}
                  myRequest={requestByTontineId.get(tontine.id)}
                  isMember={myTontineIds.has(tontine.id)}
                  busy={busy}
                  onJoin={handleJoin}
                  onCancel={handleCancel}
                  onLeave={handleLeave}
                />
              ))}
            </div>
          )}
        </section>
      )}

      <Modal
        open={showForm}
        title="Nouvelle tontine"
        description="Le cycle dure un mois par participant : le nombre de places fixe la date de fin."
        wide
        onClose={() => setShowForm(false)}
      >
        <CreateTontineForm onCreated={handleCreated} />
      </Modal>
    </div>
  );
}
