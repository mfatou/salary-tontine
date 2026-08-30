package com.salarytontine.service;

import com.salarytontine.entity.AuditLog;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journalisation des actions sensibles. Le champ details ne contient que des
 * informations métier : jamais de mot de passe, de hash ni de jeton.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Enregistre une trace. La propagation MANDATORY n'est pas utilisee afin de
     * pouvoir tracer aussi des actions hors transaction (inscription par exemple).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(User author, AuditAction action, String entityType, Long entityId, String details) {
        String safeDetails = truncate(details);
        auditLogRepository.save(new AuditLog(author, action, entityType, entityId, safeDetails));
    }

    /** Journal complet, du plus recent au plus ancien. Reserve aux administrateurs. */
    @Transactional(readOnly = true)
    public Page<AuditLog> findRecent(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findAllWithUser(pageable);
    }

    private String truncate(String details) {
        if (details == null || details.length() <= AuditLog.MAX_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, AuditLog.MAX_DETAILS_LENGTH);
    }
}
