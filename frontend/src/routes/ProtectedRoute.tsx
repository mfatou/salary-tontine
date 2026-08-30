import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '../components/Spinner';
import { useAuth } from '../hooks/useAuth';

/**
 * Reserve une branche de routes aux utilisateurs authentifies.
 * Ce controle ameliore l'experience mais ne remplace jamais
 * l'autorisation appliquee par le backend.
 */
export function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <Spinner label="Vérification de la session..." />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
