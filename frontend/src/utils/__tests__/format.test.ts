import { describe, expect, it } from 'vitest';
import { currentMonth, formatAmount, formatMonth, formatSignedAmount } from '../format';

describe('formatAmount', () => {
  it('formate un montant avec des separateurs de milliers et le suffixe FCFA', () => {
    expect(formatAmount(500000)).toMatch(/500\s?000\sFCFA/);
    expect(formatAmount(700000)).toMatch(/700\s?000\sFCFA/);
  });

  it('affiche zero pour une valeur absente', () => {
    expect(formatAmount(null)).toMatch(/0\sFCFA/);
    expect(formatAmount(undefined)).toMatch(/0\sFCFA/);
    expect(formatAmount(Number.NaN)).toMatch(/0\sFCFA/);
  });
});

describe('formatSignedAmount', () => {
  it('prefixe la cotisation et la cagnotte du bon signe', () => {
    expect(formatSignedAmount(50000, '-')).toMatch(/^-/);
    expect(formatSignedAmount(250000, '+')).toMatch(/^\+/);
  });

  it('n affiche pas de signe pour un montant nul', () => {
    expect(formatSignedAmount(0, '+')).not.toMatch(/^\+/);
  });
});

describe('formatMonth', () => {
  it('convertit un mois AAAA-MM en libelle francais', () => {
    expect(formatMonth('2026-08')).toBe('août 2026');
    expect(formatMonth('2026-12')).toBe('décembre 2026');
    expect(formatMonth('2027-01')).toBe('janvier 2027');
  });

  it('retourne un tiret pour une valeur absente', () => {
    expect(formatMonth(null)).toBe('—');
  });

  it('retourne la valeur brute si le format est inattendu', () => {
    expect(formatMonth('août')).toBe('août');
  });
});

describe('currentMonth', () => {
  it('retourne le mois courant au format AAAA-MM', () => {
    expect(currentMonth()).toMatch(/^\d{4}-(0[1-9]|1[0-2])$/);
  });
});
