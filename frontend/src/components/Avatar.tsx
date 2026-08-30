import { initials } from '../utils/initials';

interface AvatarProps {
  name: string;
  size?: 'default' | 'small';
}

/** Pastille d'initiales, utilisee partout ou une personne est nommee. */
export function Avatar({ name, size = 'default' }: AvatarProps) {
  return (
    <span className={size === 'small' ? 'avatar avatar-sm' : 'avatar'} aria-hidden="true">
      {initials(name)}
    </span>
  );
}
