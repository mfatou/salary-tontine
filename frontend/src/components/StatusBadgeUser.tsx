import type { UserStatus } from '../types';
import { USER_STATUS_LABELS } from '../utils/labels';

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{USER_STATUS_LABELS[status]}</span>;
}
