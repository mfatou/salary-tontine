package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Reponse du comptable a une demande d'adhesion.
 *
 * <p>{@code turnOrder} n'a de sens qu'a l'acceptation ; laisse vide, le
 * demandeur prend la place suivante disponible. {@code note} justifie un refus.</p>
 */
public record JoinRequestDecision(

        @Min(value = 1, message = "L'ordre de passage doit être superieur ou egal a 1.")
        @Schema(example = "3")
        Integer turnOrder,

        @Size(max = 300, message = "La note ne peut dépasser 300 caracteres.")
        @Schema(example = "Le cycle est complet pour ce trimestre.")
        String note) {
}
