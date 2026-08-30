import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { DashboardPage } from '../pages/dashboard/DashboardPage';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { AccountPage } from '../pages/account/AccountPage';
import { AuditPage } from '../pages/audit/AuditPage';
import { RegistrationsPage } from '../pages/registrations/RegistrationsPage';
import { EmployeesPage } from '../pages/employees/EmployeesPage';
import { JoinRequestsPage } from '../pages/requests/JoinRequestsPage';
import { MySalaryPage } from '../pages/salary/MySalaryPage';
import { TontineDetailPage } from '../pages/tontines/TontineDetailPage';
import { TontineListPage } from '../pages/tontines/TontineListPage';
import { UsersPage } from '../pages/users/UsersPage';
import { ProtectedRoute } from './ProtectedRoute';
import { RoleProtectedRoute } from './RoleProtectedRoute';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forbidden" element={<ForbiddenPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/tontines" element={<TontineListPage />} />
          <Route path="/tontines/:id" element={<TontineDetailPage />} />
          <Route path="/my-salary" element={<MySalaryPage />} />
          <Route path="/mon-compte" element={<AccountPage />} />

          <Route element={<RoleProtectedRoute allowedRoles={['ACCOUNTANT', 'ADMIN']} />}>
            <Route path="/demandes" element={<JoinRequestsPage />} />
            <Route path="/employés" element={<EmployeesPage />} />
          </Route>

          <Route element={<RoleProtectedRoute allowedRoles={['ADMIN']} />}>
            <Route path="/inscriptions" element={<RegistrationsPage />} />
            <Route path="/comptes" element={<UsersPage />} />
            <Route path="/audit" element={<AuditPage />} />
            {/* Ancienne console unique, scindee en deux pages. */}
            <Route path="/admin" element={<Navigate to="/comptes" replace />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
