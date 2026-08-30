import { apiClient } from './client';
import type { Dashboard, MonthlySalary, SalaryRecord } from '../types';

export const salaryApi = {
  mine: () => apiClient.get<SalaryRecord[]>('/api/salaries/me').then((response) => response.data),

  /** Bulletin consolide d'un mois, toutes tontines confondues. */
  forMonth: (month: string) =>
    apiClient.get<MonthlySalary>(`/api/salaries/me/${month}`).then((response) => response.data),
};

export const dashboardApi = {
  load: () => apiClient.get<Dashboard>('/api/dashboard').then((response) => response.data),
};
