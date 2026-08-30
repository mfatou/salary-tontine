package com.salarytontine.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Calcul pur du salaire simulé, sans accès à la base ni au contexte HTTP.
 * Tous les montants sont en {@link BigDecimal} : aucun flottant n'intervient
 * dans un calcul monétaire.
 */
@Component
public class SalaryCalculator {

    /**
     * Cagnotte du mois : cotisation mensuelle multipliee par le nombre de participants.
     */
    public BigDecimal calculatePot(BigDecimal monthlyAmount, int participantCount) {
        requireNonNegative(monthlyAmount, "Le montant mensuel");
        if (participantCount < 0) {
            throw new IllegalArgumentException("Le nombre de participants ne peut pas être negatif.");
        }
        return monthlyAmount.multiply(BigDecimal.valueOf(participantCount));
    }

    /**
     * Salaire simule d'un participant pour un mois donne.
     *
     * @param tontineReceived cagnotte percue, ou zero si le participant n'est pas bénéficiaire
     */
    public SimulatedSalary calculate(BigDecimal baseSalary,
                                     BigDecimal tontineDeduction,
                                     BigDecimal tontineReceived) {
        requireNonNegative(baseSalary, "Le salaire de base");
        requireNonNegative(tontineDeduction, "La cotisation");
        requireNonNegative(tontineReceived, "La cagnotte percue");

        BigDecimal finalSalary = baseSalary.subtract(tontineDeduction).add(tontineReceived);
        return new SimulatedSalary(baseSalary, tontineDeduction, tontineReceived, finalSalary);
    }

    private void requireNonNegative(BigDecimal amount, String label) {
        if (amount == null) {
            throw new IllegalArgumentException("%s est obligatoire.".formatted(label));
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("%s ne peut pas être negatif.".formatted(label));
        }
    }

    /** Resultat du calcul pour un participant et un mois. */
    public record SimulatedSalary(
            BigDecimal baseSalary,
            BigDecimal tontineDeduction,
            BigDecimal tontineReceived,
            BigDecimal finalSalary) {
    }
}
