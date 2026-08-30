package com.salarytontine.enums;

/**
 * Cadence des tours. MONTHLY suit le calendrier plutôt qu'un nombre de jours ;
 * CUSTOM laisse la tontine fixer sa durée, d'où le calcul des bornes porté par
 * l'entité et non ici.
 */
public enum TontineFrequency {

    WEEKLY(7),
    TEN_DAYS(10),
    BIWEEKLY(14),
    /** Cadence calendaire : la longueur du tour suit le mois. */
    MONTHLY(null),
    /** Durée libre, portée par {@code Tontine.periodDays}. */
    CUSTOM(null);

    private final Integer lengthInDays;

    TontineFrequency(Integer lengthInDays) {
        this.lengthInDays = lengthInDays;
    }

    /** Durée fixe de la cadence, ou {@code null} pour MONTHLY et CUSTOM. */
    public Integer getLengthInDays() {
        return lengthInDays;
    }

    public boolean isCalendarMonth() {
        return this == MONTHLY;
    }

    /** Vrai lorsque la durée doit être fournie par la tontine. */
    public boolean requiresExplicitLength() {
        return this == CUSTOM;
    }
}
