package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tour visé par une génération.
 *
 * <p>Le rang remplace le mois : une tontine peut tourner à la semaine ou aux
 * dix jours, cadences pour lesquelles « le mois » ne désigne aucun tour précis.</p>
 */
public record PeriodRequest(

        @NotNull(message = "Le tour est obligatoire.")
        @Min(value = 1, message = "Le rang d'un tour commence à 1.")
        @Schema(example = "1", description = "Rang du tour dans le cycle, à partir de 1.")
        Integer periodIndex) {
}
