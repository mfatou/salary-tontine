package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddMemberRequest(

        @NotNull(message = "L'identifiant de l'utilisateur est obligatoire.")
        @Positive(message = "L'identifiant de l'utilisateur est invalide.")
        @Schema(example = "4")
        Long userId,

        @NotNull(message = "L'ordre de passage est obligatoire.")
        @Min(value = 1, message = "L'ordre de passage doit être superieur ou egal a 1.")
        @Schema(example = "2")
        Integer turnOrder) {
}
