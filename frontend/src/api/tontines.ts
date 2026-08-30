import { apiClient } from './client';
import type {
  AddMemberPayload,
  Contribution,
  CreateTontinePayload,
  JoinDecisionPayload,
  JoinRequest,
  JoinTontinePayload,
  SalaryRecord,
  ScheduleEntry,
  Tontine,
  TontineDetail,
  TontineMember,
} from '../types';

export const tontineApi = {
  list: () => apiClient.get<Tontine[]>('/api/tontines').then((response) => response.data),

  /** Tontines ouvertes aux inscriptions, visibles par tous les employés. */
  listOpen: () =>
    apiClient.get<Tontine[]>('/api/tontines/open').then((response) => response.data),

  get: (id: number) =>
    apiClient.get<TontineDetail>(`/api/tontines/${id}`).then((response) => response.data),

  create: (payload: CreateTontinePayload) =>
    apiClient.post<Tontine>('/api/tontines', payload).then((response) => response.data),

  addMember: (id: number, payload: AddMemberPayload) =>
    apiClient
      .post<TontineMember>(`/api/tontines/${id}/members`, payload)
      .then((response) => response.data),

  removeMember: (id: number, userId: number) =>
    apiClient.delete<void>(`/api/tontines/${id}/members/${userId}`).then(() => undefined),

  /** Quitter une tontine encore ouverte. */
  leave: (id: number) =>
    apiClient.delete<void>(`/api/tontines/${id}/members/me`).then(() => undefined),

  cancel: (id: number) =>
    apiClient.post<Tontine>(`/api/tontines/${id}/cancel`).then((response) => response.data),

  remove: (id: number) => apiClient.delete<void>(`/api/tontines/${id}`).then(() => undefined),

  activate: (id: number) =>
    apiClient.post<Tontine>(`/api/tontines/${id}/activate`).then((response) => response.data),

  requestJoin: (id: number, payload: JoinTontinePayload = {}) =>
    apiClient
      .post<JoinRequest>(`/api/tontines/${id}/join-requests`, payload)
      .then((response) => response.data),

  cancelJoinRequest: (id: number) =>
    apiClient.delete<void>(`/api/tontines/${id}/join-requests/me`).then(() => undefined),

  listJoinRequests: (id: number) =>
    apiClient
      .get<JoinRequest[]>(`/api/tontines/${id}/join-requests`)
      .then((response) => response.data),

  acceptJoinRequest: (id: number, requestId: number, payload: JoinDecisionPayload = {}) =>
    apiClient
      .post<TontineMember>(`/api/tontines/${id}/join-requests/${requestId}/accept`, payload)
      .then((response) => response.data),

  rejectJoinRequest: (id: number, requestId: number, payload: JoinDecisionPayload = {}) =>
    apiClient
      .post<JoinRequest>(`/api/tontines/${id}/join-requests/${requestId}/reject`, payload)
      .then((response) => response.data),

  /** File d'attente du comptable : demandes en attente, toutes tontines confondues. */
  pendingJoinRequests: () =>
    apiClient.get<JoinRequest[]>('/api/join-requests/pending').then((response) => response.data),

  myJoinRequests: () =>
    apiClient.get<JoinRequest[]>('/api/join-requests/me').then((response) => response.data),

  schedule: (id: number) =>
    apiClient.get<ScheduleEntry[]>(`/api/tontines/${id}/schedule`).then((response) => response.data),

  listContributions: (id: number, periodIndex?: number) =>
    apiClient
      .get<Contribution[]>(`/api/tontines/${id}/contributions`, {
        params: periodIndex ? { periodIndex } : undefined,
      })
      .then((response) => response.data),

  generateContributions: (id: number, periodIndex: number) =>
    apiClient
      .post<Contribution[]>(`/api/tontines/${id}/contributions/generate`, { periodIndex })
      .then((response) => response.data),

  generateSalaries: (id: number, periodIndex: number) =>
    apiClient
      .post<SalaryRecord[]>(`/api/tontines/${id}/salaries/generate`, { periodIndex })
      .then((response) => response.data),
};
