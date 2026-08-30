package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salarytontine.entity.AuditLog;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.Role;
import com.salarytontine.repository.AuditLogRepository;
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("enregistre l'auteur, l'action et l'entite concernee")
    void recordsAuthorActionAndEntity() {
        User author = TestEntities.user(1L, "Manager", "manager@salarytontine.test",
                Role.ACCOUNTANT, BigDecimal.ZERO);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(call -> call.getArgument(0));

        auditService.record(author, AuditAction.TONTINE_ACTIVATED, "Tontine", 10L, "Activation");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(author);
        assertThat(saved.getAction()).isEqualTo(AuditAction.TONTINE_ACTIVATED);
        assertThat(saved.getEntityType()).isEqualTo("Tontine");
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(saved.getDetails()).isEqualTo("Activation");
    }

    @Test
    @DisplayName("tronque les détails trop longs pour respecter la contrainte de colonne")
    void truncatesOversizedDetails() {
        User author = TestEntities.manager(1L);
        String oversized = "x".repeat(AuditLog.MAX_DETAILS_LENGTH + 100);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(call -> call.getArgument(0));

        auditService.record(author, AuditAction.TONTINE_UPDATED, "Tontine", 1L, oversized);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).hasSize(AuditLog.MAX_DETAILS_LENGTH);
    }

    @Test
    @DisplayName("accepte des détails absents")
    void acceptsNullDetails() {
        User author = TestEntities.manager(1L);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(call -> call.getArgument(0));

        auditService.record(author, AuditAction.USER_REGISTERED, "User", 1L, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }
}
