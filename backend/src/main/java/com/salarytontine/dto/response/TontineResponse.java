package com.salarytontine.dto.response;

import com.salarytontine.enums.TontineFrequency;
import com.salarytontine.enums.TontineStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

public record TontineResponse(
        Long id,
        String name,
        BigDecimal monthlyAmount,
        LocalDate startDate,
        YearMonth startMonth,
        TontineStatus status,
        /** Cadence des tours. */
        TontineFrequency frequency,
        /** Durée d'un tour en jours, ou null pour une cadence calendaire. */
        Integer periodLengthInDays,
        int memberCount,
        BigDecimal potAmount,
        /** Nombre de places declare, ou null si la tontine n'en limite pas. */
        Integer targetMemberCount,
        /** Places encore disponibles, ou null si le nombre de places est libre. */
        Integer remainingSeats,
        /** Dernier mois du cycle. Previsionnel tant que la tontine est ouverte. */
        YearMonth endMonth,
        /** Dernier jour du cycle, prévisionnel tant que la tontine est ouverte. */
        LocalDate endDate,
        /** Coût mensuel moyen de la participation, cadence comprise. */
        BigDecimal monthlyCost,
        String createdByName,
        Instant createdAt) {
}
