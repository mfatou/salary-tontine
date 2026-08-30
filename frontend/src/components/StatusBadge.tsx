import type { ContributionStatus, TontineStatus } from '../types';
import { CONTRIBUTION_STATUS_LABELS, TONTINE_STATUS_LABELS } from '../utils/labels';

export function TontineStatusBadge({ status }: { status: TontineStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{TONTINE_STATUS_LABELS[status]}</span>;
}

export function ContributionStatusBadge({ status }: { status: ContributionStatus }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      {CONTRIBUTION_STATUS_LABELS[status]}
    </span>
  );
}
