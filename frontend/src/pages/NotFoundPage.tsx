import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="message-page">
      <h1>Page introuvable</h1>
      <p>La page demandee n'existe pas.</p>
      <Link to="/dashboard" className="button button-primary">
        Retour au tableau de bord
      </Link>
    </div>
  );
}
