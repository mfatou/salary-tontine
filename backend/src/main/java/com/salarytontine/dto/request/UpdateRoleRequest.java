package com.salarytontine.dto.request;

import com.salarytontine.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(

        @NotNull(message = "Le role est obligatoire.")
        Role role) {
}
