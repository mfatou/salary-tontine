import { Link } from 'react-router-dom';

export function ForbiddenPage() {
  return (
    <div className="message-page">
      <h1>Accès refusé</h1>
      <p>Votre role ne vous permet pas d'acceder a cette page.</p>
      <Link to="/dashboard" className="button button-primary">
        Retour au tableau de bord
      </Link>
    </div>
  );
}
