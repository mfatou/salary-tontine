package com.salarytontine.dto.response;

import com.salarytontine.enums.AuditAction;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String userName,
        AuditAction action,
        String entityType,
        Long entityId,
        String details,
        Instant createdAt) {
}
