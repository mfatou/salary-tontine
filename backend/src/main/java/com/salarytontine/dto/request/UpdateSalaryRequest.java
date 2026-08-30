package com.salarytontine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record UpdateSalaryRequest(

        @NotNull(message = "Le salaire de base est obligatoire.")
        @PositiveOrZero(message = "Le salaire de base ne peut pas être negatif.")
        @Digits(integer = 13, fraction = 2, message = "Le salaire de base est mal forme.")
        @Schema(example = "500000")
        BigDecimal baseSalary) {
}
