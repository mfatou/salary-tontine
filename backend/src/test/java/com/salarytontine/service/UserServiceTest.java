package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salarytontine.dto.request.UpdateRoleRequest;
import com.salarytontine.dto.request.UpdateSalaryRequest;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.Role;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.repository.UserRepository;
import com.salarytontine.security.CurrentUserProvider;
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditService auditService;

    @Mock
    private TontineRepository tontineRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private User admin;

    @BeforeEach
    void setUp() {
        // Le service de capacité tourne pour de vrai sur le dépôt simulé : la règle
        // « un administrateur n'est pas salarié » est ainsi réellement exercée.
        userService = new UserService(userRepository, passwordEncoder,
                new ContributionCapacityService(tontineRepository), currentUserProvider, auditService);
        admin = TestEntities.user(1L, "Admin", "admin@salarytontine.test", Role.ADMIN, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("modifie le salaire de base d'un employé")
    void updatesBaseSalary() {
        User employee = TestEntities.employee(2L, "Awa", BigDecimal.ZERO);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(currentUserProvider.requireUser()).thenReturn(admin);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateBaseSalary(2L, new UpdateSalaryRequest(new BigDecimal("500000")));

        assertThat(updated.getBaseSalary()).isEqualByComparingTo("500000");
        verify(auditService).record(eq(admin), eq(AuditAction.USER_SALARY_UPDATED), eq("User"), eq(2L), any());
    }

    @Test
    @DisplayName("promeut un employé au role de manager")
    void updatesRole() {
        User employee = TestEntities.employee(2L, "Awa", BigDecimal.ZERO);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(currentUserProvider.requireUser()).thenReturn(admin);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateRole(2L, new UpdateRoleRequest(Role.ACCOUNTANT));

        assertThat(updated.getRole()).isEqualTo(Role.ACCOUNTANT);
        verify(auditService).record(eq(admin), eq(AuditAction.USER_ROLE_UPDATED), eq("User"), eq(2L), any());
    }

    @Test
    @DisplayName("empeche un administrateur de retirer son propre role ADMIN")
    void preventsAdminSelfDemotion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(currentUserProvider.requireUser()).thenReturn(admin);

        assertThatThrownBy(() -> userService.updateRole(1L, new UpdateRoleRequest(Role.EMPLOYEE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("propre role");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("retourne 404 pour un utilisateur inexistant")
    void failsOnUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateBaseSalary(99L,
                new UpdateSalaryRequest(new BigDecimal("100"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
