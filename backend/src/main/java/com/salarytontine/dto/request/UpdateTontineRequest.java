package com.salarytontine.dto.request;

import com.salarytontine.enums.TontineFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modification partielle d'une tontine encore au statut DRAFT.
 * Chaque champ est optionnel : seul ce qui est fourni est applique.
 */
public record UpdateTontineRequest(

        @Size(min = 3, max = 120, message = "Le nom doit contenir entre 3 et 120 caracteres.")
        String name,

        @Positive(message = "Le montant mensuel doit être strictement positif.")
        @Digits(integer = 13, fraction = 2, message = "Le montant mensuel est mal forme.")
        BigDecimal monthlyAmount,

        @Schema(example = "2026-09-01")
        LocalDate startDate,

        @Min(value = 2, message = "Une tontine compte au moins 2 places.")
        @Max(value = 60, message = "Une tontine ne peut dépasser 60 places.")
        @Schema(example = "6")
        Integer targetMemberCount,

        @Schema(example = "WEEKLY")
        TontineFrequency frequency,

        @Min(value = 1, message = "Un tour dure au moins 1 jour.")
        @Max(value = 365, message = "Un tour ne peut dépasser 365 jours.")
        @Schema(example = "2")
        Integer periodDays) {
}
