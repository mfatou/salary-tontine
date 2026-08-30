/** Formatage des valeurs affichees. Aucun calcul metier n'est fait ici. */

const CURRENCY_SUFFIX = 'FCFA';

const MONTH_LABELS = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
];

/** Affiche un montant fictif, par exemple : 500 000 FCFA. */
export function formatAmount(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return `0 ${CURRENCY_SUFFIX}`;
  }
  const rounded = Math.round(amount * 100) / 100;
  const formatted = new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(rounded);
  return `${formatted} ${CURRENCY_SUFFIX}`;
}

/**
 * Montant issu d'une moyenne, arrondi à l'unité.
 *
 * Afficher des centimes sur une estimation suggère une exactitude qu'elle n'a
 * pas : un mois porte quatre ou cinq prélèvements hebdomadaires, jamais 4,35.
 */
export function formatApproximateAmount(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return formatAmount(0);
  }
  return formatAmount(Math.round(amount));
}

/** Affiche un montant signé, par exemple : -50 000 FCFA ou +250 000 FCFA. */
export function formatSignedAmount(amount: number, sign: '+' | '-'): string {
  if (amount === 0) {
    return formatAmount(0);
  }
  return `${sign}${formatAmount(amount)}`;
}

/** Convertit "2026-08" en "août 2026". Retourne l'entree telle quelle si le format diffère. */
export function formatMonth(month: string | null | undefined): string {
  if (!month) {
    return '—';
  }
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (!match) {
    return month;
  }
  const monthIndex = Number(match[2]) - 1;
  const label = MONTH_LABELS[monthIndex];
  return label ? `${label} ${match[1]}` : month;
}

/** Convertit une date ISO (AAAA-MM-JJ) en date lisible : « 1 août 2026 ». */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '—';
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) {
    return isoDate;
  }
  const label = MONTH_LABELS[Number(match[2]) - 1];
  return label ? `${Number(match[3])} ${label} ${match[1]}` : isoDate;
}

/** Convertit une date ISO en date et heure lisibles. */
export function formatDateTime(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '—';
  }
  const date = new Date(isoDate);
  if (Number.isNaN(date.getTime())) {
    return isoDate;
  }
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

/** Mois courant au format "YYYY-MM", utilise comme valeur par défaut des formulaires. */
export function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}
