import { apiClient } from './client';
import type { SalaryRecord, User } from '../types';

/**
 * Annuaire salarial : réservé au comptable et a l'administrateur.
 * Distinct de adminApi, qui porte les opérations sur les comptes eux-mêmes.
 */
export const employeeApi = {
  list: () => apiClient.get<User[]>('/api/employees').then((response) => response.data),

  updateSalary: (userId: number, baseSalary: number) =>
    apiClient
      .patch<User>(`/api/employees/${userId}/salary`, { baseSalary })
      .then((response) => response.data),

  salaryHistory: (userId: number) =>
    apiClient
      .get<SalaryRecord[]>(`/api/employees/${userId}/salaries`)
      .then((response) => response.data),
};
