import { useState } from 'react';
import { extractErrorMessage } from '../../api/client';
import { employeeApi } from '../../api/employees';
import { Identity } from '../../components/Identity';
import { RoleBadge } from '../../components/RoleBadge';
import { useAuth } from '../../hooks/useAuth';
import type { User } from '../../types';
import { formatAmount } from '../../utils/format';

interface SalaryRowProps {
  user: User;
  onUpdated: (updated: User) => void;
  onError: (message: string | null) => void;
}

/**
 * Ligne de l'annuaire salarial. Le comptable y corrige un salaire de base ;
 * le role, lui, releve de l'administrateur et n'est pas modifiable ici.
 */
export function SalaryRow({ user, onUpdated, onError }: SalaryRowProps) {
  const { user: currentUser } = useAuth();
  const [salary, setSalary] = useState(String(user.baseSalary));
  const [busy, setBusy] = useState(false);

  // Deux lignes échappent à la saisie : la sienne, parce que nul ne fixe sa
  // propre rémunération, et celle d'un administrateur, qui n'est pas salarié.
  // Le serveur applique les mêmes règles ; les désactiver ici évite seulement
  // de proposer une action vouée à l'échec.
  const isSelf = currentUser?.id === user.id;
  const isAdmin = user.role === 'ADMIN';
  const locked = isSelf || isAdmin;
  const dirty = Number(salary) !== Number(user.baseSalary);

  const handleSubmit = async () => {
    const parsed = Number(salary);
    if (Number.isNaN(parsed) || parsed < 0) {
      onError('Le salaire de base ne peut pas être negatif.');
      return;
    }
    setBusy(true);
    onError(null);
    try {
      onUpdated(await employeeApi.updateSalary(user.id, parsed));
    } catch (caught) {
      onError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <tr>
      <td data-label="Employé">
        <Identity name={user.name} email={user.email} />
      </td>
      <td data-label="Role">
        <RoleBadge role={user.role} />
      </td>
      <td data-label="Salaire de base" className="table-numeric">
        {isAdmin ? <span className="muted">Sans objet</span> : formatAmount(user.baseSalary)}
      </td>
      <td data-label="Modifier">
        {locked ? (
          <span className="muted">
            {isSelf
              ? 'Vous ne fixez pas votre propre salaire'
              : "L'administrateur n'est pas salarié"}
          </span>
        ) : (
          <div className="cell-actions">
            <input
              type="number"
              min="0"
              step="1000"
              aria-label={`Salaire de base de ${user.name}`}
              value={salary}
              onChange={(event) => setSalary(event.target.value)}
            />
            <button
              type="button"
              className="button button-primary button-small"
              onClick={handleSubmit}
              disabled={busy || !dirty}
            >
              Enregistrer
            </button>
          </div>
        )}
      </td>
    </tr>
  );
}
