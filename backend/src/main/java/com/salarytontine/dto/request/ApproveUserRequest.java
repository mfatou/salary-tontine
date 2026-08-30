package com.salarytontine.dto.request;

import com.salarytontine.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Validation d'une inscription.
 *
 * <p>L'administrateur attribue le rôle. Le salaire de base est facultatif ici :
 * il relève du comptable, qui le renseigne dans l'annuaire salarial.</p>
 */
public record ApproveUserRequest(

        @NotNull(message = "Le rôle est obligatoire.")
        @Schema(example = "EMPLOYEE")
        Role role,

        @DecimalMin(value = "0.0", message = "Le salaire de base ne peut pas être négatif.")
        @Digits(integer = 13, fraction = 2, message = "Le salaire de base est invalide.")
        @Schema(example = "500000")
        BigDecimal baseSalary) {
}
