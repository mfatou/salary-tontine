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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

    // -----------------------------------------------------------------------
    // Journalisation des échecs d'authentification
    //
    // La trace doit exister — sans elle, une campagne de force brute ne laisse
    // rien derrière elle — mais ne jamais contenir le mot de passe soumis.
    // -----------------------------------------------------------------------

    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger authServiceLogger;

    private List<ILoggingEvent> captureLogs() {
        authServiceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        authServiceLogger.addAppender(logAppender);
        return logAppender.list;
    }

    @AfterEach
    void detachAppender() {
        if (authServiceLogger != null && logAppender != null) {
            authServiceLogger.detachAppender(logAppender);
        }
    }

    @Test
    @DisplayName("journalise un echec pour email inconnu, sans le mot de passe")
    void logsFailureForUnknownEmail() {
        List<ILoggingEvent> events = captureLogs();
        when(userRepository.findByEmailIgnoreCase("inconnu@example.test"))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().authenticate(
                new LoginRequest("inconnu@example.test", RAW_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(events.getFirst().getFormattedMessage())
                .contains("inconnu@example.test")
                .doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("journalise un echec pour mot de passe errone, sans le mot de passe")
    void logsFailureForWrongPassword() {
        List<ILoggingEvent> events = captureLogs();
        when(userRepository.findByEmailIgnoreCase("awa@example.test"))
                .thenReturn(java.util.Optional.of(userWithPassword()));

        assertThatThrownBy(() -> service().authenticate(
                new LoginRequest("awa@example.test", "MauvaisMotDePasse1")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(events.getFirst().getFormattedMessage())
                .contains("awa@example.test")
                .doesNotContain("MauvaisMotDePasse1");
    }

    @Test
    @DisplayName("aucune trace ne contient le mot de passe, quel que soit l'echec")
    void neverLogsAnySubmittedPassword() {
        List<ILoggingEvent> events = captureLogs();
        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(java.util.Optional.of(userWithPassword()));

        String[] tentatives = {"MauvaisMotDePasse1", "Password123!", RAW_PASSWORD + "x"};
        for (String tentative : tentatives) {
            assertThatThrownBy(() -> service().authenticate(
                    new LoginRequest("awa@example.test", tentative)))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThat(events).hasSize(tentatives.length);
        for (ILoggingEvent event : events) {
            for (String tentative : tentatives) {
                assertThat(event.getFormattedMessage()).doesNotContain(tentative);
            }
        }
    }

    @Test
    @DisplayName("une authentification reussie ne produit aucune alerte")
    void successfulAuthenticationLogsNothing() {
        List<ILoggingEvent> events = captureLogs();
        when(userRepository.findByEmailIgnoreCase("awa@example.test"))
                .thenReturn(java.util.Optional.of(userWithPassword()));

        service().authenticate(new LoginRequest("awa@example.test", RAW_PASSWORD));

        assertThat(events).isEmpty();
    }

    private User userWithPassword() {
        return new User("Awa Ndiaye", "awa@example.test",
                passwordEncoder.encode(RAW_PASSWORD), Role.EMPLOYEE, BigDecimal.ZERO);
    }
}
