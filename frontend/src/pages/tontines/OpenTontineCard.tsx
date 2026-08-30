import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { JoinRequest, Tontine } from '../../types';
import { formatAmount, formatApproximateAmount, formatDate } from '../../utils/format';
import { FREQUENCY_LABELS } from '../../utils/labels';

interface OpenTontineCardProps {
  tontine: Tontine;
  /** Demande déjà envoyee par l'utilisateur sur cette tontine, le cas echeant. */
  myRequest?: JoinRequest;
  isMember: boolean;
  busy: boolean;
  onJoin: (tontineId: number, motivation?: string) => void;
  onCancel: (tontineId: number) => void;
  onLeave: (tontineId: number) => void;
}

/**
 * Tontine ouverte aux inscriptions, telle que la voit un employé.
 *
 * <p>La cagnotte affichee est celle d'aujourd'hui : elle grandira encore avec
 * chaque nouveau participant accepte, jusqu'a l'activation qui la fige.</p>
 */
export function OpenTontineCard({
  tontine,
  myRequest,
  isMember,
  busy,
  onJoin,
  onCancel,
  onLeave,
}: OpenTontineCardProps) {
  const [open, setOpen] = useState(false);
  const [motivation, setMotivation] = useState('');
  const motivationId = `motivation-${tontine.id}`;
  const full = tontine.remainingSeats === 0;
  // Nombre indicatif de prélèvements dans un mois : sans lui, un coût mensuel
  // très supérieur à la cotisation paraît sorti de nulle part.
  const drawsPerMonth = tontine.periodLengthInDays
    ? (30.44 / tontine.periodLengthInDays).toFixed(1).replace('.', ',')
    : null;

  return (
    <article className="card">
      <div className="card-header">
        <div>
          <h3 className="card-title">{tontine.name}</h3>
          <p className="card-subtitle">
            Ouverte aux inscriptions · démarre le {formatDate(tontine.startDate)}
          </p>
        </div>
        {isMember && <span className="badge badge-accepted">Vous participez</span>}
        {!isMember && myRequest?.status === 'PENDING' && (
          <span className="badge badge-pending">Demande envoyee</span>
        )}
        {!isMember && myRequest?.status === 'REJECTED' && (
          <span className="badge badge-rejected">Demande refusée</span>
        )}
      </div>

      <dl className="definition-grid">
        <div>
          <dt>Cotisation par tour</dt>
          <dd>
            {formatAmount(tontine.monthlyAmount)}
            <span className="muted"> · {FREQUENCY_LABELS[tontine.frequency]}</span>
          </dd>
        </div>
        <div>
          <dt>Coût mensuel moyen</dt>
          <dd>
            {formatApproximateAmount(tontine.monthlyCost)}
            {drawsPerMonth && (
              <span className="muted"> · ≈ {drawsPerMonth} prélèvements/mois</span>
            )}
          </dd>
        </div>
        <div>
          <dt>Participants</dt>
          <dd>
            {tontine.targetMemberCount
              ? `${tontine.memberCount} / ${tontine.targetMemberCount}`
              : tontine.memberCount}
            {tontine.targetMemberCount && (
              <span className={`seat-bar ${full ? 'seat-bar-full' : ''}`} aria-hidden="true">
                <span
                  className="seat-bar-fill"
                  style={{
                    display: 'block',
                    width: `${Math.min(100, (tontine.memberCount / tontine.targetMemberCount) * 100)}%`,
                  }}
                />
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt>Cagnotte actuelle</dt>
          <dd>{formatAmount(tontine.potAmount)}</dd>
        </div>
        <div>
          <dt>Fin du cycle</dt>
          <dd>
            {formatDate(tontine.endDate)}
            {tontine.endDate && <span className="muted"> (prévisionnel)</span>}
          </dd>
        </div>
      </dl>

      {myRequest?.status === 'REJECTED' && myRequest.decisionNote && (
        <p className="request-motivation">Motif du refus : {myRequest.decisionNote}</p>
      )}

      <div className="page-actions">
        <Link to={`/tontines/${tontine.id}`} className="button button-ghost button-small">
          Détails
        </Link>

        {!isMember && myRequest?.status === 'PENDING' && (
          <button
            type="button"
            className="button button-ghost button-small"
            disabled={busy}
            onClick={() => onCancel(tontine.id)}
          >
            Retirer ma demande
          </button>
        )}

        {isMember && (
          <button
            type="button"
            className="button button-ghost button-small"
            disabled={busy}
            onClick={() => onLeave(tontine.id)}
          >
            Quitter cette tontine
          </button>
        )}

        {!isMember && myRequest?.status !== 'PENDING' && !open && (
          <button
            type="button"
            className="button button-primary button-small"
            disabled={busy || full}
            title={full ? 'Toutes les places sont pourvues.' : undefined}
            onClick={() => setOpen(true)}
          >
            {full
              ? 'Complet'
              : myRequest?.status === 'REJECTED'
                ? 'Redemander'
                : 'Rejoindre'}
          </button>
        )}
      </div>

      {open && (
        <div style={{ marginTop: 'var(--space-sm)' }}>
          <div className="field">
            <label htmlFor={motivationId}>Message au comptable (facultatif)</label>
            <textarea
              id={motivationId}
              maxLength={300}
              value={motivation}
              placeholder="Pourquoi souhaitez-vous participer a ce cycle ?"
              onChange={(event) => setMotivation(event.target.value)}
            />
          </div>
          <div className="page-actions">
            <button
              type="button"
              className="button button-primary button-small"
              disabled={busy}
              onClick={() => onJoin(tontine.id, motivation || undefined)}
            >
              Envoyer ma demande
            </button>
            <button
              type="button"
              className="button button-ghost button-small"
              disabled={busy}
              onClick={() => setOpen(false)}
            >
              Annuler
            </button>
          </div>
        </div>
      )}
    </article>
  );
}
