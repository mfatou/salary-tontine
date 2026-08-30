package com.salarytontine.dto.response;

import com.salarytontine.enums.Role;
import com.salarytontine.enums.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        UserStatus status,
        BigDecimal baseSalary,
        Instant createdAt) {
}
