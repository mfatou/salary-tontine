export function Spinner({ label = 'Chargement...' }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      <span className="spinner-circle" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
