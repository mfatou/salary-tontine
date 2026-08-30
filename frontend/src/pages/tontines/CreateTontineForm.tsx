import { useState, type FormEvent } from 'react';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { currentMonth } from '../../utils/format';
import { ALL_FREQUENCIES, FREQUENCY_HINTS, FREQUENCY_LABELS } from '../../utils/labels';
import type { TontineFrequency } from '../../types';

interface CreateTontineFormProps {
  onCreated: () => void | Promise<void>;
}

/**
 * Le cycle dure un mois par participant : declarer le nombre de places revient
 * a fixer la fin du cycle, et permet de l'annoncer avant même l'activation.
 */
export function CreateTontineForm({ onCreated }: CreateTontineFormProps) {
  const [name, setName] = useState('');
  const [monthlyAmount, setMonthlyAmount] = useState('');
  const [startMonth, setStartMonth] = useState(currentMonth());
  const [seats, setSeats] = useState('5');
  const [frequency, setFrequency] = useState<TontineFrequency>('MONTHLY');
  const [periodDays, setPeriodDays] = useState('2');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const validate = (): string | null => {
    if (!name.trim()) {
      return 'Le nom de la tontine est obligatoire.';
    }
    const amount = Number(monthlyAmount);
    if (!monthlyAmount || Number.isNaN(amount) || amount <= 0) {
      return 'Le montant mensuel doit être strictement positif.';
    }
    if (!/^\d{4}-\d{2}$/.test(startMonth)) {
      return 'Le mois de début doit être au format AAAA-MM.';
    }
    if (seats) {
      const places = Number(seats);
      if (!Number.isInteger(places) || places < 2 || places > 60) {
        return 'Le nombre de places doit être un entier compris entre 2 et 60.';
      }
    }
    if (frequency === 'CUSTOM') {
      const jours = Number(periodDays);
      if (!Number.isInteger(jours) || jours < 1 || jours > 365) {
        return 'La durée d’un tour doit être un entier compris entre 1 et 365 jours.';
      }
    }
    return null;
  };

  // Fin de cycle déduite du départ, du nombre de places et de la cadence.
  // Le serveur reste seul juge : cet aperçu ne fait que guider la saisie.
  const endHint = (() => {
    const places = Number(seats);
    if (!/^\d{4}-\d{2}$/.test(startMonth) || !Number.isInteger(places) || places < 2) {
      return 'Le cycle compte un tour par participant.';
    }
    const [year, month] = startMonth.split('-').map(Number);

    if (frequency === 'MONTHLY') {
      const end = new Date(year, month - 1 + places, 0);
      return `${places} tours d'un mois : fin le ${end.toLocaleDateString('fr-FR')}.`;
    }
    const jours =
      frequency === 'CUSTOM'
        ? Number(periodDays)
        : { WEEKLY: 7, TEN_DAYS: 10, BIWEEKLY: 14 }[frequency];
    if (!Number.isInteger(jours) || jours < 1) {
      return 'Précisez la durée d’un tour, en jours.';
    }
    const end = new Date(year, month - 1, 1);
    end.setDate(end.getDate() + places * jours - 1);
    return `${places} tours de ${jours} jours : fin le ${end.toLocaleDateString('fr-FR')}.`;
  })();

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    try {
      await tontineApi.create({
        name: name.trim(),
        monthlyAmount: Number(monthlyAmount),
        startDate: `${startMonth}-01`,
        targetMemberCount: seats ? Number(seats) : null,
        frequency,
        periodDays: frequency === 'CUSTOM' ? Number(periodDays) : null,
      });
      setName('');
      setMonthlyAmount('');
      setSeats('5');
      setFrequency('MONTHLY');
      setPeriodDays('2');
      await onCreated();
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="card form-card" onSubmit={handleSubmit} noValidate aria-label="Créer une tontine">
      {error && <Alert variant="error">{error}</Alert>}

      <div className="form-row">
        <div className="field">
          <label htmlFor="tontine-name">Nom</label>
          <input
            id="tontine-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Tontine Equipe A"
          />
        </div>

        <div className="field">
          <label htmlFor="tontine-amount">Cotisation par tour (FCFA)</label>
          <input
            id="tontine-amount"
            type="number"
            min="1"
            step="1"
            value={monthlyAmount}
            onChange={(event) => setMonthlyAmount(event.target.value)}
            placeholder="50000"
          />
        </div>

        <div className="field">
          <label htmlFor="tontine-start">Mois de début</label>
          <input
            id="tontine-start"
            type="month"
            value={startMonth}
            onChange={(event) => setStartMonth(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="tontine-frequency">Cadence des tours</label>
          <select
            id="tontine-frequency"
            value={frequency}
            onChange={(event) => setFrequency(event.target.value as TontineFrequency)}
          >
            {ALL_FREQUENCIES.map((value) => (
              <option key={value} value={value}>
                {FREQUENCY_LABELS[value]}
              </option>
            ))}
          </select>
          <p className="field-hint">{FREQUENCY_HINTS[frequency]}</p>
        </div>

        {frequency === 'CUSTOM' && (
          <div className="field">
            <label htmlFor="tontine-period-days">Durée d'un tour (jours)</label>
            <input
              id="tontine-period-days"
              type="number"
              min="1"
              max="365"
              value={periodDays}
              onChange={(event) => setPeriodDays(event.target.value)}
            />
            <p className="field-hint">
              De 1 à 365 jours. Un tour court multiplie les prélèvements dans le mois.
            </p>
          </div>
        )}

        <div className="field">
          <label htmlFor="tontine-seats">Nombre de places</label>
          <input
            id="tontine-seats"
            type="number"
            min="2"
            max="60"
            value={seats}
            onChange={(event) => setSeats(event.target.value)}
          />
          <p className="field-hint">{endHint}</p>
        </div>
      </div>

      <button type="submit" className="button button-primary" disabled={submitting}>
        {submitting ? 'Création...' : 'Créer la tontine'}
      </button>
    </form>
  );
}
