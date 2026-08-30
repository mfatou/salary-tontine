package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Demande d'adhesion envoyee par un employé. La tontine et le demandeur sont
 * deduits de l'URL et du jeton : le client ne fournit qu'un mot libre.
 */
public record JoinTontineRequest(

        @Size(max = 300, message = "Le message ne peut dépasser 300 caracteres.")
        @Schema(example = "Je souhaite participer au cycle qui demarre en septembre.")
        String motivation) {
}
