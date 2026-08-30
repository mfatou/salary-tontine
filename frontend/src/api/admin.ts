import { apiClient } from './client';
import type { ApproveUserPayload, AuditLog, PageResponse, Role, User } from '../types';

export const adminApi = {
  listUsers: () => apiClient.get<User[]>('/api/admin/users').then((response) => response.data),

  /**
   * Valide une inscription. L'administrateur attribue le rôle mais ne choisit
   * jamais le mot de passe : celui-ci a été défini par l'employé.
   */
  approveUser: (userId: number, payload: ApproveUserPayload) =>
    apiClient
      .post<User>(`/api/admin/users/${userId}/approve`, payload)
      .then((response) => response.data),

  rejectUser: (userId: number) =>
    apiClient.post<User>(`/api/admin/users/${userId}/reject`).then((response) => response.data),

  updateRole: (userId: number, role: Role) =>
    apiClient
      .patch<User>(`/api/admin/users/${userId}/role`, { role })
      .then((response) => response.data),

  updateSalary: (userId: number, baseSalary: number) =>
    apiClient
      .patch<User>(`/api/admin/users/${userId}/salary`, { baseSalary })
      .then((response) => response.data),

  auditLogs: (page = 0, size = 50) =>
    apiClient
      .get<PageResponse<AuditLog>>('/api/admin/audit-logs', { params: { page, size } })
      .then((response) => response.data),
};
