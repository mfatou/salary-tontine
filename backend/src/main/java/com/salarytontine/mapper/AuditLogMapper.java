package com.salarytontine.mapper;

import com.salarytontine.dto.response.AuditLogResponse;
import com.salarytontine.entity.AuditLog;
import com.salarytontine.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    /**
     * Une trace sans auteur provient du planificateur mensuel, qui s'execute
     * hors de toute session utilisateur.
     */
    private static final String SYSTEM_AUTHOR_LABEL = "Système";

    public AuditLogResponse toResponse(AuditLog auditLog) {
        User author = auditLog.getUser();
        return new AuditLogResponse(
                auditLog.getId(),
                author == null ? null : author.getId(),
                author == null ? SYSTEM_AUTHOR_LABEL : author.getName(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getDetails(),
                auditLog.getCreatedAt());
    }

    public List<AuditLogResponse> toResponses(List<AuditLog> auditLogs) {
        return auditLogs.stream().map(this::toResponse).toList();
    }
}
