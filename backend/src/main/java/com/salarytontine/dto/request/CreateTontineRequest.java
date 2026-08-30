package com.salarytontine.dto.request;

import com.salarytontine.enums.TontineFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTontineRequest(

        @NotBlank(message = "Le nom de la tontine est obligatoire.")
        @Size(min = 3, max = 120, message = "Le nom doit contenir entre 3 et 120 caracteres.")
        @Schema(example = "Tontine Equipe A")
        String name,

        @NotNull(message = "Le montant mensuel est obligatoire.")
        @Positive(message = "Le montant mensuel doit être strictement positif.")
        @Digits(integer = 13, fraction = 2, message = "Le montant mensuel est mal forme.")
        @Schema(example = "50000")
        BigDecimal monthlyAmount,

        @NotNull(message = "La date de début est obligatoire.")
        @Schema(example = "2026-08-01", description = "Premier jour du mois de début du cycle.")
        LocalDate startDate,

        @Min(value = 2, message = "Une tontine compte au moins 2 places.")
        @Max(value = 60, message = "Une tontine ne peut dépasser 60 places.")
        @Schema(example = "5", description = """
                Nombre de places, facultatif. Le cycle durant un mois par participant,
                declarer les places revient a fixer d'avance la fin du cycle.""")
        Integer targetMemberCount,

        @Schema(example = "MONTHLY", description = """
                Cadence des tours : WEEKLY, TEN_DAYS, BIWEEKLY ou MONTHLY.
                Par défaut MONTHLY.""")
        TontineFrequency frequency,

        @Min(value = 1, message = "Un tour dure au moins 1 jour.")
        @Max(value = 365, message = "Un tour ne peut dépasser 365 jours.")
        @Schema(example = "2", description = """
                Durée d'un tour en jours. Obligatoire pour la cadence CUSTOM,
                ignorée pour les autres, qui portent déjà leur durée.""")
        Integer periodDays) {
}
