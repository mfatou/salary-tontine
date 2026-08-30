package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inscription publique. Le role et le salaire ne sont volontairement pas
 * exposes : ils sont imposes par le serveur.
 */
public record RegisterRequest(

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(min = 2, max = 120, message = "Le nom doit contenir entre 2 et 120 caracteres.")
        @Schema(example = "Awa Ndiaye")
        String name,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email n'est pas valide.")
        @Size(max = 180, message = "L'email ne peut dépasser 180 caracteres.")
        @Schema(example = "awa@example.test")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caracteres.")
        @Schema(example = "MotDePasse123")
        String password) {
}
