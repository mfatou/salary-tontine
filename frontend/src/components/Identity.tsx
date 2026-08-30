import { Avatar } from './Avatar';

interface IdentityProps {
  name: string;
  email?: string;
}

/** Nom et email d'une personne, precedes de ses initiales. */
export function Identity({ name, email }: IdentityProps) {
  return (
    <span className="identity">
      <Avatar name={name} size="small" />
      <span className="identity-text">
        <span className="identity-name">{name}</span>
        {email && <span className="identity-email">{email}</span>}
      </span>
    </span>
  );
}
