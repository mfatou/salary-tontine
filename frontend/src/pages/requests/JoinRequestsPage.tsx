import { useState } from 'react';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Spinner } from '../../components/Spinner';
import { useApiResource } from '../../hooks/useApiResource';
import type { JoinRequest } from '../../types';
import { JoinRequestCard } from './JoinRequestCard';

/**
 * File d'attente du comptable : toutes les demandes d'adhesion non arbitrees,
 * quelle que soit la tontine, pour eviter d'avoir a les chercher une par une.
 */
export function JoinRequestsPage() {
  const { data, loading, error, reload } = useApiResource<JoinRequest[]>(
    () => tontineApi.pendingJoinRequests(),
    [],
  );

  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const runDecision = async (
    request: JoinRequest,
    décide: () => Promise<unknown>,
    successMessage: string,
  ) => {
    setBusyId(request.id);
    setActionError(null);
    setNotice(null);
    try {
      await décide();
      setNotice(successMessage);
      await reload();
    } catch (caught) {
      setActionError(extractErrorMessage(caught));
    } finally {
      setBusyId(null);
    }
  };

  const handleAccept = (request: JoinRequest) => (requestId: number, turnOrder?: number) =>
    runDecision(
      request,
      () => tontineApi.acceptJoinRequest(request.tontineId, requestId, { turnOrder }),
      `${request.userName} participe desormais a « ${request.tontineName} ».`,
    );

  const handleReject = (request: JoinRequest) => (requestId: number, note?: string) =>
    runDecision(
      request,
      () => tontineApi.rejectJoinRequest(request.tontineId, requestId, { note }),
      `La demande de ${request.userName} a été refusée.`,
    );

  const requests = data ?? [];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Demandes d'adhesion</h1>
          <p className="page-subtitle">
            Les employés qui souhaitent rejoindre une tontine ouverte. Accepter attribue un ordre
            de passage et fige la place dans le cycle.
          </p>
        </div>
      </header>

      {actionError && <Alert variant="error">{actionError}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}

      {loading && <Spinner label="Chargement des demandes..." />}
      {error && <Alert variant="error">{error}</Alert>}

      {!loading && !error && requests.length === 0 && (
        <EmptyState
          title="Aucune demande en attente"
          description="Les demandes des employés apparaitront ici des qu'une tontine ouverte en recevra."
        />
      )}

      {requests.length > 0 && (
        <section className="request-list" aria-label="Demandes en attente">
          {requests.map((request) => (
            <JoinRequestCard
              key={request.id}
              request={request}
              showTontine
              busy={busyId === request.id}
              onAccept={handleAccept(request)}
              onReject={handleReject(request)}
            />
          ))}
        </section>
      )}
    </div>
  );
}
