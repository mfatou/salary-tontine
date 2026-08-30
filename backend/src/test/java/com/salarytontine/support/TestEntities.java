package com.salarytontine.support;

import com.salarytontine.entity.Contribution;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.TontineFrequency;
import com.salarytontine.enums.TontineStatus;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fabriques d'entites pour les tests unitaires.
 * Les identifiants sont normalement générés par la base ; ils sont injectes ici
 * par reflexion afin de pouvoir tester la logique metier sans persistance.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(Long id, String name, String email, Role role, BigDecimal baseSalary) {
        return withId(new User(name, email, "$2a$10$hash-de-test", role, baseSalary), id);
    }

    public static User employee(Long id, String name, BigDecimal baseSalary) {
        return user(id, name, name.toLowerCase().replace(" ", ".") + "@salarytontine.test",
                Role.EMPLOYEE, baseSalary);
    }

    public static User manager(Long id) {
        return user(id, "Manager Demo", "manager@salarytontine.test", Role.ACCOUNTANT, BigDecimal.ZERO);
    }

    public static Tontine tontine(Long id, BigDecimal monthlyAmount, YearMonth startMonth, User createdBy) {
        Tontine tontine = new Tontine("Tontine de test", monthlyAmount, startMonth.atDay(1), createdBy);
        return withId(tontine, id);
    }

    /** Tontine à cadence choisie, pour les tests de découpage du cycle. */
    public static Tontine tontine(Long id, BigDecimal monthlyAmount, LocalDate startDate,
                                  User createdBy, TontineFrequency frequency) {
        return tontine(id, monthlyAmount, startDate, createdBy, frequency, null);
    }

    /** Variante à cadence libre : la durée du tour est fournie en jours. */
    public static Tontine tontine(Long id, BigDecimal monthlyAmount, LocalDate startDate,
                                  User createdBy, TontineFrequency frequency, Integer periodDays) {
        return withId(new Tontine("Tontine de test", monthlyAmount, startDate, createdBy,
                null, frequency, periodDays), id);
    }

    public static Tontine activeTontine(Long id, BigDecimal monthlyAmount, YearMonth startMonth, User createdBy) {
        Tontine tontine = tontine(id, monthlyAmount, startMonth, createdBy);
        tontine.setStatus(TontineStatus.ACTIVE);
        return tontine;
    }

    /** Ajoute un membre a la tontine en respectant la coherence bidirectionnelle. */
    public static TontineMember addMember(Tontine tontine, User user, int turnOrder, Long memberId) {
        TontineMember member = withId(new TontineMember(tontine, user, turnOrder), memberId);
        tontine.addMember(member);
        return member;
    }

    public static Contribution contribution(Long id, Tontine tontine, User user, int periodIndex) {
        return withId(new Contribution(tontine, user, tontine.getMonthlyAmount(),
                periodIndex, tontine.periodStart(periodIndex)), id);
    }

    private static <T> T withId(T entity, Long id) {
        if (id == null) {
            return entity;
        }
        try {
            Field idField = findIdField(entity.getClass());
            idField.setAccessible(true);
            idField.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Impossible d'injecter l'identifiant sur " + entity.getClass().getSimpleName(), exception);
        }
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Aucun champ 'id' sur " + type.getName());
    }
}
