package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salarytontine.dto.request.LoginRequest;
import com.salarytontine.dto.request.RegisterRequest;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.Role;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String RAW_PASSWORD = "MotDePasse123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    private AuthService service() {
        if (authService == null) {
            authService = new AuthService(userRepository, passwordEncoder, auditService);
        }
        return authService;
    }

    @Test
    @DisplayName("inscrit un utilisateur avec le role EMPLOYEE et un salaire a zero")
    void registersUserWithForcedRoleAndZeroSalary() {
        when(userRepository.existsByEmailIgnoreCase("awa@example.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service().register(new RegisterRequest("Awa Ndiaye", "awa@example.test", RAW_PASSWORD));

        assertThat(created.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(created.getBaseSalary()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getName()).isEqualTo("Awa Ndiaye");
    }

    @Test
    @DisplayName("normalise l'email en minuscules et supprime les espaces")
    void normalizesEmail() {
        when(userRepository.existsByEmailIgnoreCase("awa@example.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service().register(new RegisterRequest("Awa", "  AWA@Example.TEST ", RAW_PASSWORD));

        assertThat(created.getEmail()).isEqualTo("awa@example.test");
    }

    @Test
    @DisplayName("hashe le mot de passe et ne le stocke jamais en clair")
    void hashesPassword() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service().register(new RegisterRequest("Awa", "awa@example.test", RAW_PASSWORD));

        assertThat(created.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("refuse une inscription avec un email déjà utilise")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("awa@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service().register(
                new RegisterRequest("Awa", "awa@example.test", RAW_PASSWORD)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("déjà utilise");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("trace l'inscription dans le journal d'audit")
    void recordsRegistrationInAuditLog() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().register(new RegisterRequest("Awa", "awa@example.test", RAW_PASSWORD));

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(User.class), eq(AuditAction.USER_REGISTERED),
                eq("User"), any(), details.capture());
        assertThat(details.getValue()).doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("authentifie un utilisateur avec le bon mot de passe")
    void authenticatesWithValidCredentials() {
        User stored = userWithPassword();
        when(userRepository.findByEmailIgnoreCase("awa@example.test"))
                .thenReturn(java.util.Optional.of(stored));

        User authenticated = service().authenticate(new LoginRequest("awa@example.test", RAW_PASSWORD));

        assertThat(authenticated).isSameAs(stored);
    }

    @Test
    @DisplayName("refuse un mot de passe errone")
    void rejectsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("awa@example.test"))
                .thenReturn(java.util.Optional.of(userWithPassword()));

        assertThatThrownBy(() -> service().authenticate(
                new LoginRequest("awa@example.test", "MauvaisMotDePasse")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("refuse un email inconnu avec le même message qu'un mot de passe errone")
    void rejectsUnknownEmailWithGenericMessage() {
        when(userRepository.findByEmailIgnoreCase("inconnu@example.test"))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().authenticate(
                new LoginRequest("inconnu@example.test", RAW_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiants invalides.");
    }

    private User userWithPassword() {
        return new User("Awa Ndiaye", "awa@example.test",
                passwordEncoder.encode(RAW_PASSWORD), Role.EMPLOYEE, BigDecimal.ZERO);
    }
}
