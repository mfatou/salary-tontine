import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar } from '../components/Avatar';
import { adminApi } from '../api/admin';
import { tontineApi } from '../api/tontines';
import { useAuth } from '../hooks/useAuth';
import type { Role } from '../types';
import { ROLE_LABELS } from '../utils/labels';

interface NavigationItem {
  to: string;
  label: string;
  /** Rôles autorisés ; absent signifie visible par tout utilisateur connecté. */
  roles?: Role[];
  /** Source du compteur affiché en pastille, le cas échéant. */
  badge?: 'joinRequests' | 'registrations';
}

interface NavigationGroup {
  title: string;
  items: NavigationItem[];
}

/** La navigation est textuelle : des pictogrammes n'ajouteraient rien à des
 *  libellés déjà explicites, et alourdiraient la lecture. */
const NAVIGATION: NavigationGroup[] = [
  {
    title: 'Général',
    items: [
      { to: '/dashboard', label: 'Tableau de bord' },
      { to: '/tontines', label: 'Tontines' },
      { to: '/my-salary', label: 'Mon salaire' },
      { to: '/mon-compte', label: 'Mon compte' },
    ],
  },
  {
    title: 'Gestion',
    items: [
      {
        to: '/demandes',
        label: 'Demandes',
        roles: ['ACCOUNTANT', 'ADMIN'],
        badge: 'joinRequests',
      },
      { to: '/employés', label: 'Employés et salaires', roles: ['ACCOUNTANT', 'ADMIN'] },
    ],
  },
  {
    title: 'Administration',
    items: [
      { to: '/inscriptions', label: 'Inscriptions', roles: ['ADMIN'], badge: 'registrations' },
      { to: '/comptes', label: 'Comptes', roles: ['ADMIN'] },
      { to: '/audit', label: "Journal d'audit", roles: ['ADMIN'] },
    ],
  },
];

export function AppLayout() {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [joinRequestCount, setJoinRequestCount] = useState(0);
  const [registrationCount, setRegistrationCount] = useState(0);

  const canManage = hasRole('ACCOUNTANT', 'ADMIN');
  const isAdmin = hasRole('ADMIN');

  // Le compteur est un rappel d'action, pas une donnée critique : un echec de
  // chargement laisse simplement la pastille masquee. Le drapeau d'annulation
  // evite d'ecrire dans un etat demonte si la reponse arrive après coup.
  useEffect(() => {
    let cancelled = false;

    void (async () => {
      if (canManage) {
        try {
          const requests = await tontineApi.pendingJoinRequests();
          if (!cancelled) {
            setJoinRequestCount(requests.length);
          }
        } catch {
          if (!cancelled) {
            setJoinRequestCount(0);
          }
        }
      } else if (!cancelled) {
        setJoinRequestCount(0);
      }

      if (isAdmin) {
        try {
          const accounts = await adminApi.listUsers();
          if (!cancelled) {
            setRegistrationCount(accounts.filter((account) => account.status === 'PENDING').length);
          }
        } catch {
          if (!cancelled) {
            setRegistrationCount(0);
          }
        }
      } else if (!cancelled) {
        setRegistrationCount(0);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [canManage, isAdmin, location.pathname]);

  // La navigation referme le tiroir ouvert sur petit ecran.
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const badgeCount = (item: NavigationItem) => {
    if (item.badge === 'joinRequests') {
      return joinRequestCount;
    }
    return item.badge === 'registrations' ? registrationCount : 0;
  };

  const visibleGroups = NAVIGATION.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.roles || hasRole(...item.roles)),
  })).filter((group) => group.items.length > 0);

  return (
    <div className="app-shell">
      <aside
        className={`app-sidebar ${menuOpen ? 'app-sidebar-open' : ''}`}
        aria-label="Navigation principale"
      >
        <NavLink to="/dashboard" className="sidebar-brand">
          Salary<span>Tontine</span>
        </NavLink>

        <nav className="sidebar-nav">
          {visibleGroups.map((group) => (
            <div className="nav-group" key={group.title}>
              <p className="nav-group-title">{group.title}</p>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => (isActive ? 'nav-link nav-link-active' : 'nav-link')}
                >
                  {item.label}
                  {badgeCount(item) > 0 && (
                    <span className="nav-count">
                      {badgeCount(item)}
                      <span className="sr-only"> en attente</span>
                    </span>
                  )}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        {user && (
          <div className="sidebar-user">
            <Avatar name={user.name} />
            <span className="sidebar-user-info">
              <span className="sidebar-user-name">{user.name}</span>
              <span className="sidebar-user-role">{ROLE_LABELS[user.role]}</span>
            </span>
            <button type="button" className="sidebar-logout" onClick={handleLogout}>
              Déconnexion
            </button>
          </div>
        )}
      </aside>

      {menuOpen && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label="Fermer le menu"
          onClick={() => setMenuOpen(false)}
        />
      )}

      <div className="app-content">
        <div className="app-topbar">
          <button
            type="button"
            className="nav-toggle"
            aria-expanded={menuOpen}
            aria-label="Ouvrir le menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            ☰
          </button>
          <span className="topbar-brand">
            Salary<span>Tontine</span>
          </span>
        </div>

        <main className="app-main">
          <Outlet />
        </main>

        <footer className="app-footer">
          SalaryTontine — application de gestion de tontines.
        </footer>
      </div>
    </div>
  );
}
