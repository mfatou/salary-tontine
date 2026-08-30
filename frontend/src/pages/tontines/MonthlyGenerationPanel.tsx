import { useState } from 'react';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import type { ScheduleEntry } from '../../types';
import { formatDate } from '../../utils/format';

type GenerationKind = 'contributions' | 'salaries';

interface MonthlyGenerationPanelProps {
  tontineId: number;
  /** Calendrier du cycle : sert à choisir le tour à traiter. */
  schedule: ScheduleEntry[];
  onGenerated: () => void | Promise<void>;
}

/**
 * Déclenche le traitement d'un tour.
 *
 * <p>Le traitement automatique s'en charge chaque jour ; ce panneau sert à
 * démontrer un cycle complet sans attendre, et à rattraper un tour manqué.</p>
 */
export function MonthlyGenerationPanel({
  tontineId,
  schedule,
  onGenerated,
}: MonthlyGenerationPanelProps) {
  const [periodIndex, setPeriodIndex] = useState(schedule[0]?.periodIndex ?? 1);
  const [pendingKind, setPendingKind] = useState<GenerationKind | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const selected = schedule.find((entry) => entry.periodIndex === periodIndex);
  const periodLabel = selected
    ? `du ${formatDate(selected.start)} au ${formatDate(selected.end)}`
    : `n° ${periodIndex}`;

  const confirm = async () => {
    if (!pendingKind) {
      return;
    }
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      if (pendingKind === 'contributions') {
        const created = await tontineApi.generateContributions(tontineId, periodIndex);
        setSuccess(`${created.length} cotisations générées pour le tour ${periodIndex}.`);
      } else {
        const created = await tontineApi.generateSalaries(tontineId, periodIndex);
        setSuccess(`${created.length} salaires simulés générés pour le tour ${periodIndex}.`);
      }
      await onGenerated();
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
      setPendingKind(null);
    }
  };

  return (
    <div className="card">
      <h2 className="card-title">Traitement d'un tour</h2>
      <p className="muted">
        Le traitement automatique passe chaque jour et rattrape les tours échus. Ce panneau sert
        à dérouler un cycle sans attendre, ou à reprendre un tour resté en arrière. Les
        cotisations précèdent toujours les salaires, et tous les montants sont calculés par le
        serveur.
      </p>

      {error && <Alert variant="error">{error}</Alert>}
      {success && <Alert variant="success">{success}</Alert>}

      <div className="inline-form">
        <div className="field">
          <label htmlFor="generation-period">Tour</label>
          <select
            id="generation-period"
            value={periodIndex}
            onChange={(event) => setPeriodIndex(Number(event.target.value))}
          >
            {schedule.map((entry) => (
              <option key={entry.periodIndex} value={entry.periodIndex}>
                Tour {entry.periodIndex} — {formatDate(entry.start)} · {entry.beneficiaryName}
              </option>
            ))}
          </select>
        </div>

        <button
          type="button"
          className="button button-primary"
          onClick={() => setPendingKind('contributions')}
          disabled={busy || schedule.length === 0}
        >
          Générer les cotisations
        </button>

        <button
          type="button"
          className="button button-secondary"
          onClick={() => setPendingKind('salaries')}
          disabled={busy || schedule.length === 0}
        >
          Générer les salaires
        </button>
      </div>

      <ConfirmDialog
        open={pendingKind !== null}
        title={
          pendingKind === 'salaries'
            ? 'Générer les salaires simulés ?'
            : 'Générer les cotisations ?'
        }
        message={
          pendingKind === 'salaries'
            ? `Les salaires simulés du tour ${periodIndex} (${periodLabel}) vont être calculés et enregistrés. Cette opération ne peut pas être annulée depuis l'interface.`
            : `Une cotisation va être créée pour chaque participant au titre du tour ${periodIndex} (${periodLabel}). Cette opération ne peut pas être annulée depuis l'interface.`
        }
        confirmLabel="Générer"
        busy={busy}
        onConfirm={confirm}
        onCancel={() => setPendingKind(null)}
      />
    </div>
  );
}
