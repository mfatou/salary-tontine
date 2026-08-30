package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email n'est pas valide.")
        @Schema(example = "awa@salarytontine.test")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password) {
}
