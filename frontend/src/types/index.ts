/**
 * Types miroirs des DTO exposes par l'API SalaryTontine.
 * Les montants transitent en nombre : le backend reste seul responsable
 * de tout calcul monetaire.
 */

export type Role = 'EMPLOYEE' | 'ACCOUNTANT' | 'ADMIN';

/** Cycle de vie d'un compte : l'inscription est libre, l'accès est validé. */
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED';

export type JoinRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

/** Cadence des tours d'une tontine. */
export type TontineFrequency = 'WEEKLY' | 'TEN_DAYS' | 'BIWEEKLY' | 'MONTHLY' | 'CUSTOM';

export type TontineStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export type ContributionStatus = 'PENDING' | 'DEDUCTED' | 'CANCELLED';

export type AuditAction =
  | 'TONTINE_CREATED'
  | 'TONTINE_UPDATED'
  | 'TONTINE_ACTIVATED'
  | 'TONTINE_COMPLETED'
  | 'MEMBER_ADDED'
  | 'MEMBER_REMOVED'
  | 'CONTRIBUTIONS_GENERATED'
  | 'SALARIES_GENERATED'
  | 'USER_ROLE_UPDATED'
  | 'USER_SALARY_UPDATED'
  | 'USER_REGISTERED'
  | 'USER_CREATED'
  | 'JOIN_REQUESTED'
  | 'JOIN_REQUEST_CANCELLED'
  | 'JOIN_REQUEST_ACCEPTED'
  | 'JOIN_REQUEST_REJECTED'
  | 'MEMBER_LEFT'
  | 'TONTINE_CANCELLED'
  | 'TONTINE_DELETED'
  | 'USER_APPROVED'
  | 'USER_REJECTED'
  | 'USER_PASSWORD_CHANGED';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
  baseSalary: number;
  createdAt: string;
}

export interface Tontine {
  id: number;
  name: string;
  monthlyAmount: number;
  startDate: string;
  startMonth: string;
  status: TontineStatus;
  /** Cadence des tours. */
  frequency: TontineFrequency;
  /** Durée d'un tour en jours, ou null pour une cadence calendaire. */
  periodLengthInDays: number | null;
  memberCount: number;
  potAmount: number;
  /** Nombre de places declare, ou null si la tontine n'en limite pas. */
  targetMemberCount: number | null;
  /** Places restantes, ou null quand le nombre de places est libre. */
  remainingSeats: number | null;
  /** Dernier mois du cycle. Prévisionnel tant que la tontine est ouverte. */
  endMonth: string | null;
  /** Dernier jour du cycle. */
  endDate: string | null;
  /** Coût mensuel réel de la participation, cadence comprise. */
  monthlyCost: number;
  createdByName: string;
  createdAt: string;
}

export interface TontineMember {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  turnOrder: number;
}

export interface TontineDetail {
  tontine: Tontine;
  members: TontineMember[];
}

/** Demande d'un employé pour rejoindre une tontine ouverte aux inscriptions. */
export interface JoinRequest {
  id: number;
  tontineId: number;
  tontineName: string;
  userId: number;
  userName: string;
  userEmail: string;
  status: JoinRequestStatus;
  motivation: string | null;
  decisionNote: string | null;
  requestedAt: string;
  decidedAt: string | null;
  decidedByName: string | null;
}

export interface ScheduleEntry {
  periodIndex: number;
  start: string;
  end: string;
  beneficiaryUserId: number;
  beneficiaryName: string;
  turnOrder: number;
}

export interface Contribution {
  id: number;
  tontineId: number;
  tontineName: string;
  userId: number;
  userName: string;
  amount: number;
  contributionMonth: string;
  periodIndex: number;
  periodStart: string;
  status: ContributionStatus;
}

export interface SalaryRecord {
  id: number;
  userId: number;
  userName: string;
  tontineId: number;
  tontineName: string;
  salaryMonth: string;
  baseSalary: number;
  tontineDeduction: number;
  tontineReceived: number;
  finalSalary: number;
  beneficiary: boolean;
}

export interface AuditLog {
  id: number;
  userId: number | null;
  userName: string;
  action: AuditAction;
  entityType: string;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Dashboard {
  user: User;
  baseSalary: number;
  activeTontine: Tontine | null;
  myTurnOrder: number | null;
  /** Premier jour du tour où l'employé encaisse la cagnotte. */
  myTurnDate: string | null;
  nextBeneficiary: ScheduleEntry | null;
  latestSalaryRecord: SalaryRecord | null;
  /** Un employé peut cotiser à plusieurs tontines à la fois. */
  activeTontineCount: number;
  /** Cagnotte qu'il encaissera à son tour. */
  myTurnPotAmount: number | null;
  /** Salaire estimé le mois où il encaisse : base − cotisations + cagnotte. */
  projectedTurnSalary: number | null;
}

/** Bulletin consolide d'un mois, toutes tontines confondues. */
export interface MonthlySalary {
  month: string;
  baseSalary: number;
  totalDeduction: number;
  totalReceived: number;
  finalSalary: number;
  lines: SalaryRecord[];
}

/** Structure d'erreur normalisee renvoyee par le backend. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  validationErrors?: Record<string, string>;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
}

export interface CreateTontinePayload {
  name: string;
  monthlyAmount: number;
  startDate: string;
  /** Nombre de places : fixe d'avance la fin du cycle. */
  targetMemberCount?: number | null;
  /** Cadence des tours. MONTHLY par défaut. */
  frequency?: TontineFrequency;
  /** Durée d'un tour en jours. Obligatoire pour la cadence CUSTOM. */
  periodDays?: number | null;
}

export interface AddMemberPayload {
  userId: number;
  turnOrder: number;
}

/** Validation d'une inscription : le mot de passe n'y figure jamais. */
export interface ApproveUserPayload {
  role: Role;
  baseSalary?: number | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface JoinTontinePayload {
  motivation?: string;
}

/** Reponse du comptable : turnOrder a l'acceptation, note au refus. */
export interface JoinDecisionPayload {
  turnOrder?: number;
  note?: string;
}
