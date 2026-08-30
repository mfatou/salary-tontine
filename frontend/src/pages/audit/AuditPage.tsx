import { useMemo, useState } from 'react';
import { adminApi } from '../../api/admin';
import { Alert } from '../../components/Alert';
import { EmptyState } from '../../components/EmptyState';
import { Identity } from '../../components/Identity';
import { Spinner } from '../../components/Spinner';
import { useApiResource } from '../../hooks/useApiResource';
import type { AuditAction, AuditLog, PageResponse } from '../../types';
import { formatDateTime } from '../../utils/format';
import { AUDIT_ACTION_LABELS } from '../../utils/labels';

const PAGE_SIZE = 50;

/** Journal des actions sensibles. Consultation seule, réservée a l'ADMIN. */
export function AuditPage() {
  const [page, setPage] = useState(0);
  const [actionFilter, setActionFilter] = useState<AuditAction | 'ALL'>('ALL');

  const { data, loading, error } = useApiResource<PageResponse<AuditLog>>(
    () => adminApi.auditLogs(page, PAGE_SIZE),
    [page],
  );

  const logs = data?.content ?? [];

  // Le filtre porte sur la page chargee : il affine la lecture sans multiplier
  // les allers-retours serveur.
  const visible = useMemo(
    () => (actionFilter === 'ALL' ? logs : logs.filter((log) => log.action === actionFilter)),
    [logs, actionFilter],
  );

  const presentActions = useMemo(
    () => Array.from(new Set(logs.map((log) => log.action))).sort(),
    [logs],
  );

  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Journal d'audit</h1>
          <p className="page-subtitle">
            Trace des actions sensibles : comptes, roles, salaires, tontines et générations
            mensuelles. Les traces signees « Système » proviennent du traitement automatique.
          </p>
        </div>
      </header>

      {loading && <Spinner label="Chargement du journal..." />}
      {error && <Alert variant="error">{error}</Alert>}

      {!loading && !error && logs.length === 0 && (
        <EmptyState
          title="Aucune trace d'audit"
          description="Les actions sensibles seront listees ici des qu'elles auront lieu."
        />
      )}

      {logs.length > 0 && (
        <>
          <div className="field" style={{ maxWidth: '280px' }}>
            <label htmlFor="audit-action">Filtrer par action</label>
            <select
              id="audit-action"
              value={actionFilter}
              onChange={(event) => setActionFilter(event.target.value as AuditAction | 'ALL')}
            >
              <option value="ALL">Toutes les actions</option>
              {presentActions.map((action) => (
                <option key={action} value={action}>
                  {AUDIT_ACTION_LABELS[action] ?? action}
                </option>
              ))}
            </select>
          </div>

          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Auteur</th>
                  <th scope="col">Action</th>
                  <th scope="col">Entite</th>
                  <th scope="col">Détails</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((log) => (
                  <tr key={log.id}>
                    <td data-label="Date">{formatDateTime(log.createdAt)}</td>
                    <td data-label="Auteur">
                      <Identity name={log.userName} />
                    </td>
                    <td data-label="Action">
                      <span className="badge">
                        {AUDIT_ACTION_LABELS[log.action] ?? log.action}
                      </span>
                    </td>
                    <td data-label="Entite">
                      {log.entityType}
                      {log.entityId !== null && ` #${log.entityId}`}
                    </td>
                    <td data-label="Détails">{log.details ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {visible.length === 0 && (
            <p className="muted">Aucune trace pour ce filtre sur la page courante.</p>
          )}

          {totalPages > 1 && (
            <div className="page-actions">
              <button
                type="button"
                className="button button-ghost button-small"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                ← Précédent
              </button>
              <span className="muted">
                Page {page + 1} sur {totalPages} · {data?.totalElements ?? 0} traces
              </span>
              <button
                type="button"
                className="button button-ghost button-small"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Suivant →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
