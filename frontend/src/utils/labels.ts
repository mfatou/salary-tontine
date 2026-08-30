import type {
  AuditAction,
  ContributionStatus,
  JoinRequestStatus,
  Role,
  TontineFrequency,
  TontineStatus,
  UserStatus,
} from '../types';

/** Libelles francais des enums exposes par l'API. */

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employé',
  ACCOUNTANT: 'Comptable',
  ADMIN: 'Administrateur',
};

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  PENDING: 'En attente de validation',
  ACTIVE: 'Actif',
  REJECTED: 'Refusé',
};

export const FREQUENCY_LABELS: Record<TontineFrequency, string> = {
  WEEKLY: 'Chaque semaine',
  TEN_DAYS: 'Tous les 10 jours',
  BIWEEKLY: 'Tous les 15 jours',
  MONTHLY: 'Chaque mois',
  CUSTOM: 'Personnalisée',
};

/** Durée d'un tour, affichée à côté du libellé pour lever toute ambiguïté. */
export const FREQUENCY_HINTS: Record<TontineFrequency, string> = {
  WEEKLY: '7 jours par tour',
  TEN_DAYS: '10 jours par tour',
  BIWEEKLY: '14 jours par tour',
  MONTHLY: 'un mois calendaire par tour',
  CUSTOM: 'durée libre, à préciser en jours',
};

export const ALL_FREQUENCIES: TontineFrequency[] = [
  'MONTHLY',
  'BIWEEKLY',
  'TEN_DAYS',
  'WEEKLY',
  'CUSTOM',
];

export const TONTINE_STATUS_LABELS: Record<TontineStatus, string> = {
  DRAFT: 'Brouillon',
  ACTIVE: 'Active',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
};

export const JOIN_REQUEST_STATUS_LABELS: Record<JoinRequestStatus, string> = {
  PENDING: 'En attente',
  ACCEPTED: 'Acceptee',
  REJECTED: 'Refusée',
};

export const CONTRIBUTION_STATUS_LABELS: Record<ContributionStatus, string> = {
  PENDING: 'En attente',
  DEDUCTED: 'Déduite',
  CANCELLED: 'Annulée',
};

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  TONTINE_CREATED: 'Tontine créée',
  TONTINE_UPDATED: 'Tontine modifiée',
  TONTINE_ACTIVATED: 'Tontine activée',
  TONTINE_COMPLETED: 'Cycle terminé',
  MEMBER_ADDED: 'Participant ajoute',
  MEMBER_REMOVED: 'Participant retire',
  CONTRIBUTIONS_GENERATED: 'Cotisations générées',
  SALARIES_GENERATED: 'Salaires générés',
  USER_ROLE_UPDATED: 'Role modifié',
  USER_SALARY_UPDATED: 'Salaire modifié',
  USER_REGISTERED: 'Inscription',
  USER_CREATED: 'Compte créé',
  JOIN_REQUESTED: 'Demande d adhesion',
  JOIN_REQUEST_CANCELLED: 'Demande retiree',
  JOIN_REQUEST_ACCEPTED: 'Adhesion acceptee',
  JOIN_REQUEST_REJECTED: 'Adhesion refusée',
  MEMBER_LEFT: 'Depart volontaire',
  TONTINE_CANCELLED: 'Tontine annulée',
  TONTINE_DELETED: 'Tontine supprimée',
  USER_APPROVED: 'Inscription validée',
  USER_REJECTED: 'Inscription refusée',
  USER_PASSWORD_CHANGED: 'Mot de passe modifié',
};

/** Description courte de chaque rôle, affichée dans la console d'administration. */
export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  EMPLOYEE: 'Consulte son salaire et demande à rejoindre une tontine.',
  ACCOUNTANT: 'Gère les tontines, arbitre les adhésions et voit tous les salaires.',
  ADMIN: 'Crée les comptes et attribue les rôles.',
};

export const ALL_ROLES: Role[] = ['EMPLOYEE', 'ACCOUNTANT', 'ADMIN'];
