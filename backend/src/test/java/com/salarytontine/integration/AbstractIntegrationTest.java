package com.salarytontine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.repository.AuditLogRepository;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.repository.SalaryRecordRepository;
import com.salarytontine.repository.TontineJoinRequestRepository;
import com.salarytontine.repository.TontineMemberRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle des tests d'integration : PostgreSQL réel via Testcontainers,
 * migrations Flyway appliquees, pile Spring Security complete.
 * H2 est volontairement ecarte pour tester le même moteur qu'en production.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String TEST_PASSWORD = "MotDePasseTest123";
    protected static final String AUTH_COOKIE_NAME = "salarytontine_token";

    /**
     * Un unique conteneur est partage par toutes les classes de test.
     * Il est demarre une fois puis arrête par le hook d'extinction de la JVM.
     */
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("salarytontine_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "secret-de-test-integration-suffisamment-long-pour-hs256");
        registry.add("app.jwt.cookie-name", () -> AUTH_COOKIE_NAME);
        registry.add("app.seed.enabled", () -> "false");
        // Chaque test créé ses propres comptes : aucun amorcage automatique,
        // même si l'environnement du poste porte des APP_ADMIN_*.
        registry.add("app.admin.email", () -> "");
        registry.add("app.admin.password", () -> "");
        // Les tests restent maitres de l'horloge : aucune génération spontanee.
        registry.add("app.scheduling.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TontineRepository tontineRepository;

    @Autowired
    protected TontineMemberRepository tontineMemberRepository;

    @Autowired
    protected TontineJoinRequestRepository joinRequestRepository;

    @Autowired
    protected ContributionRepository contributionRepository;

    @Autowired
    protected SalaryRecordRepository salaryRecordRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Chaque test part d'une base vide pour rester independant des autres. */
    @BeforeEach
    void clearDatabase() {
        salaryRecordRepository.deleteAll();
        contributionRepository.deleteAll();
        joinRequestRepository.deleteAll();
        tontineMemberRepository.deleteAll();
        tontineRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User persistUser(String name, String email, Role role, String baseSalary) {
        return userRepository.save(new User(name, email,
                passwordEncoder.encode(TEST_PASSWORD), role, new BigDecimal(baseSalary)));
    }

    protected User persistEmployee(String name, String email, String baseSalary) {
        return persistUser(name, email, Role.EMPLOYEE, baseSalary);
    }

    /** Réalise une connexion réelle et retourne le cookie d'authentification obtenu. */
    protected Cookie login(String email) throws Exception {
        return login(email, TEST_PASSWORD);
    }

    /** Variante pour les comptes dont le mot de passe n'est pas celui par défaut. */
    protected Cookie login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(AUTH_COOKIE_NAME);
        if (cookie == null) {
            throw new IllegalStateException("Aucun cookie d'authentification retourne par /api/auth/login");
        }
        return cookie;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
