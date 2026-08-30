import type {
  AuditLog,
  Dashboard,
  Role,
  SalaryRecord,
  ScheduleEntry,
  Tontine,
  TontineDetail,
  User,
} from '../types';

/** Fabriques de données pour les tests. Elles ne servent jamais a l'application. */

export function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    name: 'Awa Ndiaye',
    email: 'awa@salarytontine.test',
    role: 'EMPLOYEE' as Role,
    status: 'ACTIVE',
    baseSalary: 500000,
    createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

export function makeTontine(overrides: Partial<Tontine> = {}): Tontine {
  return {
    id: 10,
    name: 'Tontine Equipe A',
    monthlyAmount: 50000,
    startDate: '2026-08-01',
    startMonth: '2026-08',
    status: 'ACTIVE',
    memberCount: 5,
    potAmount: 250000,
    frequency: 'MONTHLY',
    periodLengthInDays: null,
    targetMemberCount: 5,
    remainingSeats: 0,
    endMonth: '2026-12',
    endDate: '2026-12-31',
    monthlyCost: 50000,
    createdByName: 'Comptable Demo',
    createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

export function makeSalaryRecord(overrides: Partial<SalaryRecord> = {}): SalaryRecord {
  return {
    id: 100,
    userId: 1,
    userName: 'Awa Ndiaye',
    tontineId: 10,
    tontineName: 'Tontine Equipe A',
    salaryMonth: '2026-08',
    baseSalary: 500000,
    tontineDeduction: 50000,
    tontineReceived: 250000,
    finalSalary: 700000,
    beneficiary: true,
    ...overrides,
  };
}

export function makeDashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    user: makeUser(),
    baseSalary: 500000,
    activeTontine: makeTontine(),
    myTurnOrder: 1,
    myTurnDate: '2026-08-01',
    nextBeneficiary: {
      periodIndex: 1,
    start: '2026-08-01',
    end: '2026-08-31',
      beneficiaryUserId: 1,
      beneficiaryName: 'Awa Ndiaye',
      turnOrder: 1,
    },
    latestSalaryRecord: makeSalaryRecord(),
    activeTontineCount: 1,
    myTurnPotAmount: 250000,
    projectedTurnSalary: 700000,
    ...overrides,
  };
}

export function makeTontineDetail(overrides: Partial<TontineDetail> = {}): TontineDetail {
  return {
    tontine: makeTontine(),
    members: [
      { id: 1, userId: 1, userName: 'Awa Ndiaye', userEmail: 'awa@salarytontine.test', turnOrder: 1 },
      { id: 2, userId: 2, userName: 'Fatou Fall', userEmail: 'fatou@salarytontine.test', turnOrder: 2 },
    ],
    ...overrides,
  };
}

export function makeSchedule(): ScheduleEntry[] {
  return [
    {
      periodIndex: 1,
      start: '2026-08-01',
      end: '2026-08-31',
      beneficiaryUserId: 1,
      beneficiaryName: 'Awa Ndiaye',
      turnOrder: 1,
    },
    {
      periodIndex: 2,
      start: '2026-09-01',
      end: '2026-09-30',
      beneficiaryUserId: 2,
      beneficiaryName: 'Fatou Fall',
      turnOrder: 2,
    },
  ];
}

export function makeAuditLog(overrides: Partial<AuditLog> = {}): AuditLog {
  return {
    id: 1,
    userId: 3,
    userName: 'Manager Demo',
    action: 'TONTINE_ACTIVATED',
    entityType: 'Tontine',
    entityId: 10,
    details: "Activation de 'Tontine Equipe A'",
    createdAt: '2026-08-01T12:00:00Z',
    ...overrides,
  };
}
