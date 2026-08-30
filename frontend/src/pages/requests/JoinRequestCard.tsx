import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Identity } from '../../components/Identity';
import type { JoinRequest } from '../../types';
import { formatDateTime } from '../../utils/format';
import { JOIN_REQUEST_STATUS_LABELS } from '../../utils/labels';

interface JoinRequestCardProps {
  request: JoinRequest;
  /** Affiche le nom de la tontine : utile dans une liste multi-tontines. */
  showTontine?: boolean;
  busy?: boolean;
  onAccept?: (requestId: number, turnOrder?: number) => void;
  onReject?: (requestId: number, note?: string) => void;
}

/**
 * Une demande d'adhesion vue par le comptable.
 *
 * <p>L'ordre de passage reste facultatif : laisse vide, le serveur place le
 * demandeur a la suite. Le comptable ne le renseigne que pour imposer un rang.</p>
 */
export function JoinRequestCard({
  request,
  showTontine = false,
  busy = false,
  onAccept,
  onReject,
}: JoinRequestCardProps) {
  const [turnOrder, setTurnOrder] = useState('');
  const [note, setNote] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const actionable = request.status === 'PENDING' && Boolean(onAccept || onReject);
  const turnOrderId = `turn-order-${request.id}`;
  const noteId = `reject-note-${request.id}`;

  return (
    <article className={`request-item request-item-${request.status.toLowerCase()}`}>
      <div className="request-head">
        <Identity name={request.userName} email={request.userEmail} />
        <span className={`badge badge-${request.status.toLowerCase()}`}>
          {JOIN_REQUEST_STATUS_LABELS[request.status]}
        </span>
      </div>

      {showTontine && (
        <p className="request-meta">
          Tontine :{' '}
          <Link to={`/tontines/${request.tontineId}`}>{request.tontineName}</Link>
        </p>
      )}

      {request.motivation && <p className="request-motivation">« {request.motivation} »</p>}

      <p className="request-meta">
        Demande envoyee le {formatDateTime(request.requestedAt)}
        {request.decidedAt && ` · traitée le ${formatDateTime(request.decidedAt)}`}
        {request.decidedByName && ` par ${request.decidedByName}`}
      </p>

      {request.decisionNote && <p className="request-motivation">Motif : {request.decisionNote}</p>}

      {actionable && !rejecting && (
        <div className="request-actions">
          <div className="field field-narrow" style={{ marginBottom: 0 }}>
            <label htmlFor={turnOrderId}>Ordre de passage</label>
            <input
              id={turnOrderId}
              type="number"
              min={1}
              value={turnOrder}
              placeholder="auto"
              onChange={(event) => setTurnOrder(event.target.value)}
            />
          </div>
          <button
            type="button"
            className="button button-success button-small"
            disabled={busy}
            onClick={() => onAccept?.(request.id, turnOrder ? Number(turnOrder) : undefined)}
          >
            Accepter
          </button>
          <button
            type="button"
            className="button button-ghost button-small"
            disabled={busy}
            onClick={() => setRejecting(true)}
          >
            Refuser
          </button>
        </div>
      )}

      {actionable && rejecting && (
        <div className="request-actions">
          <div className="field" style={{ marginBottom: 0, flex: 1, minWidth: '220px' }}>
            <label htmlFor={noteId}>Motif du refus (facultatif)</label>
            <input
              id={noteId}
              type="text"
              maxLength={300}
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </div>
          <button
            type="button"
            className="button button-danger button-small"
            disabled={busy}
            onClick={() => onReject?.(request.id, note || undefined)}
          >
            Confirmer le refus
          </button>
          <button
            type="button"
            className="button button-ghost button-small"
            disabled={busy}
            onClick={() => setRejecting(false)}
          >
            Annuler
          </button>
        </div>
      )}
    </article>
  );
}
