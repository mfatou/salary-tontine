package com.salarytontine.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vue agregee destinee au tableau de bord de l'utilisateur authentifie.
 * Les champs lies a la tontine sont absents lorsque l'utilisateur ne participe
 * a aucune tontine active.
 */
public record DashboardResponse(
        UserResponse user,
        BigDecimal baseSalary,
        TontineResponse activeTontine,
        Integer myTurnOrder,
        /** Premier jour du tour où l'employé encaisse la cagnotte. */
        LocalDate myTurnDate,
        ScheduleEntryResponse nextBeneficiary,
        SalaryRecordResponse latestSalaryRecord,
        /** Nombre de tontines actives : un employé peut en cumuler plusieurs. */
        int activeTontineCount,
        /** Cagnotte qu'il encaissera à son tour. */
        BigDecimal myTurnPotAmount,
        /** Salaire estimé le mois où il encaisse : base − cotisations + cagnotte. */
        BigDecimal projectedTurnSalary) {
}
