package com.salarytontine.service;

import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.exception.BusinessRuleException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Déroulement d'un cycle : un tour par participant, attribué dans l'ordre des
 * {@code turnOrder}. Le rang d'un tour ne dépend d'aucune cadence, ce qui
 * permet au reste du code d'ignorer cette variabilité.
 */
@Service
public class TontineCycleService {

    /** Le premier tour du cycle correspond à l'ordre de passage 1. */
    private static final int FIRST_TURN_ORDER = 1;

    /** Nombre de tours du cycle : un par participant. */
    public int cycleLength(Tontine tontine) {
        return tontine.getMembers().size();
    }

    /**
     * Vérifie que le rang demandé appartient au cycle.
     * Lève une exception explicite plutôt que de laisser un calcul aboutir
     * silencieusement sur un tour qui n'existe pas.
     */
    public void requireWithinCycle(Tontine tontine, int periodIndex) {
        int length = cycleLength(tontine);

        if (periodIndex < FIRST_TURN_ORDER) {
            throw new BusinessRuleException(
                    "Le tour %d précède le début du cycle.".formatted(periodIndex));
        }
        if (periodIndex > length) {
            throw new BusinessRuleException(
                    "Le tour %d dépasse la fin du cycle : la tontine compte %d participants et se termine le %s."
                            .formatted(periodIndex, length, lastPeriodEnd(tontine)));
        }
    }

    /** Rang du tour couvrant la date donnée, validé contre les bornes du cycle. */
    public int resolvePeriodIndex(Tontine tontine, LocalDate date) {
        int index = tontine.periodIndexOf(date);
        requireWithinCycle(tontine, index);
        return index;
    }

    /** Participant qui reçoit la cagnotte au tour demandé. */
    public TontineMember resolveBeneficiary(Tontine tontine, int periodIndex) {
        requireWithinCycle(tontine, periodIndex);
        return tontine.getMembers().stream()
                .filter(member -> periodIndex == member.getTurnOrder())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Aucun participant ne porte l'ordre de passage %d.".formatted(periodIndex)));
    }

    /** Tour attribué à un participant donné. */
    public int resolvePeriodFor(TontineMember member) {
        return member.getTurnOrder();
    }

    /** Dernier jour du cycle. */
    public LocalDate lastPeriodEnd(Tontine tontine) {
        int length = cycleLength(tontine);
        return length == 0 ? tontine.getStartDate() : tontine.periodEnd(length);
    }

    public boolean isLastPeriodOfCycle(Tontine tontine, int periodIndex) {
        return periodIndex == cycleLength(tontine);
    }

    /**
     * Tours déjà échus à la date donnée, c'est-à-dire ceux dont le premier jour
     * est atteint. Sert au traitement automatique, qui rattrape ainsi tout tour
     * non encore généré quelle que soit la cadence.
     */
    public List<Integer> duePeriods(Tontine tontine, LocalDate date) {
        int length = cycleLength(tontine);
        if (length == 0 || date.isBefore(tontine.getStartDate())) {
            return List.of();
        }
        int reached = Math.min(
                tontine.periodIndexOf(date), length);
        return java.util.stream.IntStream.rangeClosed(FIRST_TURN_ORDER, reached).boxed().toList();
    }

    /** Calendrier prévisionnel complet, du premier au dernier tour. */
    public List<CycleSlot> buildSchedule(Tontine tontine) {
        return tontine.getMembers().stream()
                .sorted(Comparator.comparing(TontineMember::getTurnOrder))
                .map(member -> new CycleSlot(
                        member.getTurnOrder(),
                        tontine.periodStart(member.getTurnOrder()),
                        tontine.periodEnd(member.getTurnOrder()),
                        member))
                .toList();
    }

    /** Un tour du cycle : son rang, ses bornes et son bénéficiaire. */
    public record CycleSlot(int periodIndex, LocalDate start, LocalDate end, TontineMember beneficiary) {
    }
}
