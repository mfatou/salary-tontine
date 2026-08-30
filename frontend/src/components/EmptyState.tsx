interface EmptyStateProps {
  title: string;
  description?: string;
  children?: React.ReactNode;
}

/** Etat vide explicite, prefere a une page blanche quand aucune donnée n'existe. */
export function EmptyState({ title, description, children }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <p className="empty-state-title">{title}</p>
      {description && <p className="empty-state-description">{description}</p>}
      {children}
    </div>
  );
}
