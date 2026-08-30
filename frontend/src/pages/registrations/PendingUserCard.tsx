import { useState } from 'react';
import { Identity } from '../../components/Identity';
import type { Role, User } from '../../types';
import { formatDateTime } from '../../utils/format';
import { ALL_ROLES, ROLE_DESCRIPTIONS, ROLE_LABELS } from '../../utils/labels';

interface PendingUserCardProps {
  user: User;
  busy: boolean;
  onApprove: (userId: number, role: Role, baseSalary: number | null) => void;
  onReject: (userId: number) => void;
}

/**
 * Inscription en attente de validation.
 *
 * <p>L'administrateur attribue un rôle et, s'il le souhaite, un salaire de
 * base. Il ne voit ni ne choisit le mot de passe : celui-ci a été défini par
 * l'employé à l'inscription.</p>
 */
export function PendingUserCard({ user, busy, onApprove, onReject }: PendingUserCardProps) {
  const [role, setRole] = useState<Role>('EMPLOYEE');
  const [baseSalary, setBaseSalary] = useState('');

  const roleId = `pending-role-${user.id}`;
  const salaryId = `pending-salary-${user.id}`;

  return (
    <article className="request-item request-item-pending">
      <div className="request-head">
        <Identity name={user.name} email={user.email} />
        <span className="request-meta">Inscrit le {formatDateTime(user.createdAt)}</span>
      </div>

      <div className="request-actions">
        <div className="field" style={{ marginBottom: 0, minWidth: '190px' }}>
          <label htmlFor={roleId}>Rôle à attribuer</label>
          <select
            id={roleId}
            value={role}
            onChange={(event) => setRole(event.target.value as Role)}
          >
            {ALL_ROLES.map((value) => (
              <option key={value} value={value}>
                {ROLE_LABELS[value]}
              </option>
            ))}
          </select>
        </div>

        <div className="field field-narrow" style={{ marginBottom: 0 }}>
          <label htmlFor={salaryId}>Salaire de base</label>
          <input
            id={salaryId}
            type="number"
            min="0"
            step="1000"
            value={baseSalary}
            placeholder="facultatif"
            onChange={(event) => setBaseSalary(event.target.value)}
          />
        </div>

        <button
          type="button"
          className="button button-primary button-small"
          disabled={busy}
          onClick={() => onApprove(user.id, role, baseSalary ? Number(baseSalary) : null)}
        >
          Valider l'inscription
        </button>

        <button
          type="button"
          className="button button-ghost button-small"
          disabled={busy}
          onClick={() => onReject(user.id)}
        >
          Refuser
        </button>
      </div>

      <p className="field-hint">{ROLE_DESCRIPTIONS[role]}</p>
    </article>
  );
}
