import { useState } from 'react';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { Spinner } from '../../components/Spinner';
import { useApiResource } from '../../hooks/useApiResource';
import type { JoinRequest } from '../../types';
import { JoinRequestCard } from '../requests/JoinRequestCard';

interface TontineRequestsPanelProps {
  tontineId: number;
  /** Les inscriptions ne sont ouvertes que sur une tontine au statut DRAFT. */
  openForEnrollment: boolean;
  onAccepted: () => Promise<void>;
}

/** Demandes d'adhesion reçues par une tontine, arbitrees sur place. */
export function TontineRequestsPanel({
  tontineId,
  openForEnrollment,
  onAccepted,
}: TontineRequestsPanelProps) {
  const { data, loading, error, reload } = useApiResource<JoinRequest[]>(
    () => tontineApi.listJoinRequests(tontineId),
    [tontineId],
  );

  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const decide = async (requestId: number, action: () => Promise<unknown>) => {
    setBusyId(requestId);
    setActionError(null);
    try {
      await action();
      await Promise.all([reload(), onAccepted()]);
    } catch (caught) {
      setActionError(extractErrorMessage(caught));
    } finally {
      setBusyId(null);
    }
  };

  const requests = data ?? [];
  const pending = requests.filter((request) => request.status === 'PENDING');
  const settled = requests.filter((request) => request.status !== 'PENDING');

  return (
    <section className="card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Demandes d'adhesion</h2>
          <p className="card-subtitle">
            {openForEnrollment
              ? 'Accepter attribue un ordre de passage et ajoute le participant au cycle.'
              : 'Les inscriptions sont closes : la composition est figée depuis l activation.'}
          </p>
        </div>
        {pending.length > 0 && <span className="badge badge-pending">{pending.length} en attente</span>}
      </div>

      {actionError && <Alert variant="error">{actionError}</Alert>}
      {loading && <Spinner label="Chargement des demandes..." />}
      {error && <Alert variant="error">{error}</Alert>}

      {!loading && !error && requests.length === 0 && (
        <p className="muted">Aucune demande reçue pour cette tontine.</p>
      )}

      {pending.length > 0 && (
        <div className="request-list">
          {pending.map((request) => (
            <JoinRequestCard
              key={request.id}
              request={request}
              busy={busyId === request.id}
              onAccept={
                openForEnrollment
                  ? (requestId, turnOrder) =>
                      decide(requestId, () =>
                        tontineApi.acceptJoinRequest(tontineId, requestId, { turnOrder }),
                      )
                  : undefined
              }
              onReject={
                openForEnrollment
                  ? (requestId, note) =>
                      decide(requestId, () =>
                        tontineApi.rejectJoinRequest(tontineId, requestId, { note }),
                      )
                  : undefined
              }
            />
          ))}
        </div>
      )}

      {settled.length > 0 && (
        <>
          <h3 className="section-title" style={{ marginTop: 'var(--space-md)' }}>
            Demandes traitées
          </h3>
          <div className="request-list">
            {settled.map((request) => (
              <JoinRequestCard key={request.id} request={request} />
            ))}
          </div>
        </>
      )}
    </section>
  );
}
