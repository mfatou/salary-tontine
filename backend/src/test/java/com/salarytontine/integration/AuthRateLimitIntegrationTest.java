package com.salarytontine.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Limitation de débit sur les points d'entrée publics d'authentification.
 *
 * <p>Le quota par défaut est de dix tentatives par minute et par origine. Les
 * comptes visés ici n'existent pas : aucune vérification BCrypt n'est déclenchée,
 * ce qui rend le test rapide tout en incrémentant bien le compteur, celui-ci
 * étant tenu par un filtre placé avant toute la chaîne d'authentification.</p>
 */
@DisplayName("Integration - Limitation des tentatives d'authentification")
class AuthRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final int QUOTA = 10;

    private static final String UNKNOWN_LOGIN = """
            {"email":"inconnu@salarytontine.test","password":"MauvaisMotDePasse1"}
            """;

    private static final String REGISTER_BODY = """
            {"name":"Visiteur %d","email":"visiteur%d@salarytontine.test","password":"MotDePasse123"}
            """;

    @Test
    @DisplayName("un usage normal n'est jamais bloque")
    void normalUsageIsNeverThrottled() throws Exception {
        persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");

        // Trois connexions réussies d'affilée : le cas nominal reste fluide.
        for (int attempt = 0; attempt < 3; attempt++) {
            login("awa@salarytontine.test");
        }
    }

    @Test
    @DisplayName("au-dela du quota, /api/auth/login repond 429")
    void loginIsThrottledBeyondQuota() throws Exception {
        for (int attempt = 1; attempt <= QUOTA; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UNKNOWN_LOGIN))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("au-dela du quota, /api/auth/register repond 429")
    void registerIsThrottledBeyondQuota() throws Exception {
        for (int attempt = 1; attempt <= QUOTA; attempt++) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY.formatted(attempt, attempt)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted(99, 99)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("les deux points d'entree ont des quotas independants")
    void loginAndRegisterHaveSeparateQuotas() throws Exception {
        for (int attempt = 1; attempt <= QUOTA; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UNKNOWN_LOGIN))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_LOGIN))
                .andExpect(status().isTooManyRequests());

        // L'inscription dispose de son propre compteur : elle reste disponible.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted(1, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("les routes hors authentification ne sont pas limitees")
    void otherEndpointsAreNotThrottled() throws Exception {
        for (int attempt = 1; attempt <= QUOTA + 5; attempt++) {
            mockMvc.perform(get("/api/dashboard"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
