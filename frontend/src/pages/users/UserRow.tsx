import { useState } from 'react';
import { adminApi } from '../../api/admin';
import { extractErrorMessage } from '../../api/client';
import { UserStatusBadge } from '../../components/StatusBadgeUser';
import type { Role, User } from '../../types';
import { formatAmount } from '../../utils/format';
import { ALL_ROLES, ROLE_LABELS } from '../../utils/labels';

interface UserRowProps {
  user: User;
  onUpdated: (updated: User) => void;
  onError: (message: string | null) => void;
}

export function UserRow({ user, onUpdated, onError }: UserRowProps) {
  const [salary, setSalary] = useState(String(user.baseSalary));
  const [busy, setBusy] = useState(false);

  const handleRoleChange = async (role: Role) => {
    setBusy(true);
    onError(null);
    try {
      onUpdated(await adminApi.updateRole(user.id, role));
    } catch (caught) {
      onError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const handleSalarySubmit = async () => {
    const parsed = Number(salary);
    if (Number.isNaN(parsed) || parsed < 0) {
      onError('Le salaire de base ne peut pas être negatif.');
      return;
    }
    setBusy(true);
    onError(null);
    try {
      onUpdated(await adminApi.updateSalary(user.id, parsed));
    } catch (caught) {
      onError(extractErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <tr>
      <td data-label="Nom">{user.name}</td>
      <td data-label="Email">{user.email}</td>
      <td data-label="Statut">
        <UserStatusBadge status={user.status} />
      </td>
      <td data-label="Rôle">
        <select
          aria-label={`Rôle de ${user.name}`}
          value={user.role}
          disabled={busy}
          onChange={(event) => handleRoleChange(event.target.value as Role)}
        >
          {ALL_ROLES.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </select>
      </td>
      <td data-label="Salaire fictif" className="table-numeric">{formatAmount(user.baseSalary)}</td>
      <td data-label="Modifier le salaire">
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
            onClick={handleSalarySubmit}
            disabled={busy}
          >
            Enregistrer
          </button>
        </div>
      </td>
    </tr>
  );
}
