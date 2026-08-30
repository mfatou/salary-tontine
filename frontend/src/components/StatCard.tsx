interface StatCardProps {
  label: string;
  value: string;
  hint?: string;
  variant?: 'default' | 'positive' | 'negative' | 'highlight';
}

export function StatCard({ label, value, hint, variant = 'default' }: StatCardProps) {
  return (
    <div className={`stat-card stat-card-${variant}`}>
      <span className="stat-card-label">{label}</span>
      <span className="stat-card-value">{value}</span>
      {hint && <span className="stat-card-hint">{hint}</span>}
    </div>
  );
}
