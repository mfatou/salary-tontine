import { apiClient } from './client';
import type { ChangePasswordPayload } from '../types';

/** Actions qu'un utilisateur exerce sur son propre compte. */
export const accountApi = {
  changePassword: (payload: ChangePasswordPayload) =>
    apiClient.patch<void>('/api/users/me/password', payload).then(() => undefined),
};
