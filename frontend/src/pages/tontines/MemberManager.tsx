import { useState, type FormEvent } from 'react';
import { employeeApi } from '../../api/employees';
import { extractErrorMessage } from '../../api/client';
import { tontineApi } from '../../api/tontines';
import { Alert } from '../../components/Alert';
import { useApiResource } from '../../hooks/useApiResource';
import type { TontineMember, User } from '../../types';

interface MemberManagerProps {
  tontineId: number;
  members: TontineMember[];
  /** Un utilisateur ne peut être ajouté ou retiré que sur une tontine DRAFT. */
  editable: boolean;
  onChanged: () => void | Promise<void>;
}

export function MemberManager({ tontineId, members, editable, onChanged }: MemberManagerProps) {
  const [userId, setUserId] = useState('');
  const [turnOrder, setTurnOrder] = useState(String(members.length + 1));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // L'annuaire salarial est ouvert au comptable comme à l'administrateur, ce
  // qui évite au comptable de saisir un identifiant numérique à la main.
  const { data: users } = useApiResource<User[] | null>(
    () => employeeApi.list().catch(() => null),
    [],
  );

  const memberIds = new Set(members.map((member) => member.userId));
  // Un compte non validé ne peut pas participer : il n'a pas encore d'accès.
  const candidates =
    users?.filter((user) => !memberIds.has(user.id) && user.status === 'ACTIVE') ?? null;

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    const parsedUserId = Number(userId);
    const parsedTurnOrder = Number(turnOrder);
    if (!parsedUserId || Number.isNaN(parsedUserId)) {
      setError('Sélectionnez un participant.');
      return;
    }
    if (!parsedTurnOrder || parsedTurnOrder < 1) {
      setError("L'ordre de passage doit être supérieur ou égal à 1.");
      return;
    }

    setSubmitting(true);
    try {
      await tontineApi.addMember(tontineId, { userId: parsedUserId, turnOrder: parsedTurnOrder });
      setUserId('');
      setTurnOrder(String(members.length + 2));
      await onChanged();
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (memberUserId: number) => {
    setError(null);
    setSubmitting(true);
    try {
      await tontineApi.removeMember(tontineId, memberUserId);
      await onChanged();
    } catch (caught) {
      setError(extractErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card">
      <h2 className="card-title">Participants ({members.length})</h2>
      {error && <Alert variant="error">{error}</Alert>}

      {members.length === 0 ? (
        <p className="muted">Aucun participant pour le moment.</p>
      ) : (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Ordre</th>
                <th scope="col">Nom</th>
                <th scope="col">Email</th>
                {editable && <th scope="col"><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.id}>
                  <td data-label="Ordre">{member.turnOrder}</td>
                  <td data-label="Nom">{member.userName}</td>
                  <td data-label="Email">{member.userEmail}</td>
                  {editable && (
                    <td data-label="Actions">
                      <button
                        type="button"
                        className="button button-danger button-small"
                        onClick={() => handleRemove(member.userId)}
                        disabled={submitting}
                      >
                        Retirer
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editable && (
        <form className="inline-form" onSubmit={handleAdd} noValidate aria-label="Ajouter un participant">
          <div className="field">
            <label htmlFor="member-user">Participant</label>
            {candidates ? (
              <select
                id="member-user"
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
              >
                <option value="">Sélectionner…</option>
                {candidates.map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.name} ({candidate.email})
                  </option>
                ))}
              </select>
            ) : (
              <input
                id="member-user"
                type="number"
                min="1"
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
                placeholder="Identifiant utilisateur"
              />
            )}
          </div>

          <div className="field field-narrow">
            <label htmlFor="member-turn-order">Ordre de passage</label>
            <input
              id="member-turn-order"
              type="number"
              min="1"
              value={turnOrder}
              onChange={(event) => setTurnOrder(event.target.value)}
            />
          </div>

          <button type="submit" className="button button-primary" disabled={submitting}>
            Ajouter
          </button>
        </form>
      )}
    </div>
  );
}
