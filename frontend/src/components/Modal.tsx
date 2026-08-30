import { useEffect, type ReactNode } from 'react';

interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  /** Elargit la fenetre pour les formulaires a plusieurs colonnes. */
  wide?: boolean;
  onClose: () => void;
  children: ReactNode;
}

/**
 * Fenetre modale generique.
 *
 * <p>La touche Echap ferme, le defilement de la page est bloque tant qu'elle
 * est ouverte, et un clic sur le fond revient a fermer.</p>
 */
export function Modal({ open, title, description, wide = false, onClose, children }: ModalProps) {
  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div className="dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className={`dialog modal ${wide ? 'modal-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h2 id="modal-title" className="modal-title">
              {title}
            </h2>
            {description && <p className="card-subtitle">{description}</p>}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Fermer">
            <span aria-hidden="true">×</span>
          </button>
        </div>

        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
