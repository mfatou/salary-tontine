import { Navigate, Outlet } from 'react-router-dom';
import { Spinner } from '../components/Spinner';
import { useAuth } from '../hooks/useAuth';
import type { Role } from '../types';

/** Restreint une branche de routes a certains roles. */
export function RoleProtectedRoute({ allowedRoles }: { allowedRoles: Role[] }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <Spinner label="Vérification des droits..." />;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!allowedRoles.includes(user.role)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}
