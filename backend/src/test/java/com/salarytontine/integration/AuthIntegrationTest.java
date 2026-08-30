package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("Integration - Authentification")
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTER_BODY = """
            {"name":"Awa Ndiaye","email":"awa@example.test","password":"MotDePasseTest123"}
            """;

    @Test
    @DisplayName("POST /api/auth/register crée un EMPLOYEE avec un salaire a zero")
    void registersEmployee() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("awa@example.test"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.baseSalary").value(0))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User stored = userRepository.findByEmailIgnoreCase("awa@example.test").orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(stored.getPasswordHash()).isNotEqualTo("MotDePasseTest123").startsWith("$2");
    }

    @Test
    @DisplayName("POST /api/auth/register ignore un role envoye par le client")
    void ignoresClientSuppliedRole() throws Exception {
        String bodyWithRole = """
                {"name":"Pirate","email":"pirate@example.test","password":"MotDePasseTest123",
                 "role":"ADMIN","baseSalary":9999999}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithRole))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.baseSalary").value(0));
    }

    @Test
    @DisplayName("POST /api/auth/register retourne 409 si l'email existe déjà")
    void rejectsDuplicateEmail() throws Exception {
        persistEmployee("Awa", "awa@example.test", "0");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/auth/register retourne 400 sur données invalides")
    void rejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"A","email":"pas-un-email","password":"court"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login depose un cookie HttpOnly")
    void loginSetsHttpOnlyCookie() throws Exception {
        persistEmployee("Awa", "awa@example.test", "500000");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"awa@example.test","password":"%s"}
                                """.formatted(TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AUTH_COOKIE_NAME))
                .andExpect(cookie().httpOnly(AUTH_COOKIE_NAME, true))
                .andExpect(jsonPath("$.email").value("awa@example.test"));
    }

    @Test
    @DisplayName("POST /api/auth/login retourne 401 avec un mauvais mot de passe")
    void rejectsWrongPassword() throws Exception {
        persistEmployee("Awa", "awa@example.test", "500000");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"awa@example.test","password":"MauvaisMotDePasse1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me retourne le profil authentifie")
    void returnsCurrentUser() throws Exception {
        persistEmployee("Awa Ndiaye", "awa@example.test", "500000");
        Cookie session = login("awa@example.test");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Awa Ndiaye"))
                .andExpect(jsonPath("$.baseSalary").value(500000.00));
    }

    @Test
    @DisplayName("GET /api/users/me retourne 401 sans authentification")
    void rejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/users/me retourne 401 avec un jeton falsifie")
    void rejectsForgedToken() throws Exception {
        mockMvc.perform(get("/api/users/me").cookie(new Cookie(AUTH_COOKIE_NAME, "jeton.falsifie.abc")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout invalide le cookie")
    void logoutExpiresCookie() throws Exception {
        persistEmployee("Awa", "awa@example.test", "500000");
        Cookie session = login("awa@example.test");

        mockMvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AUTH_COOKIE_NAME, 0));
    }
}
