package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changement de mot de passe par son propriétaire.
 *
 * <p>Le mot de passe actuel est exigé : personne, pas même un administrateur,
 * ne peut modifier celui d'autrui.</p>
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Le mot de passe actuel est obligatoire.")
        @Schema(example = "MotDePasseActuel123")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
        @Schema(example = "NouveauMotDePasse456")
        String newPassword) {
}
