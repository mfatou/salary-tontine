import { apiClient } from './client';
import type { LoginPayload, RegisterPayload, User } from '../types';

export const authApi = {
  register: (payload: RegisterPayload) =>
    apiClient.post<User>('/api/auth/register', payload).then((response) => response.data),

  login: (payload: LoginPayload) =>
    apiClient.post<User>('/api/auth/login', payload).then((response) => response.data),

  logout: () => apiClient.post<void>('/api/auth/logout').then(() => undefined),

  me: () => apiClient.get<User>('/api/auth/me').then((response) => response.data),
};
