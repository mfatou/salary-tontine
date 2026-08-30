import type { Role } from '../types';
import { ROLE_LABELS } from '../utils/labels';

export function RoleBadge({ role }: { role: Role }) {
  return <span className={`badge badge-role-${role.toLowerCase()}`}>{ROLE_LABELS[role]}</span>;
}
