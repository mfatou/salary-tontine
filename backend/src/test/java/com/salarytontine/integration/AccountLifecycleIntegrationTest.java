package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Cycle de vie d'un compte : inscription libre, validation, mot de passe.
 *
 * <p>Le point tenu de bout en bout est qu'aucun administrateur ne connaît ni
 * ne choisit le mot de passe d'un employé. Il valide l'inscription et attribue
 * le rôle ; le mot de passe reste l'affaire de son propriétaire.</p>
 */
@DisplayName("Integration - Cycle de vie d'un compte")
class AccountLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String NEW_EMAIL = "ndeye@salarytontine.test";
    private static final String NEW_PASSWORD = "MotDePasseNdeye1";

    private User admin;
    private Cookie adminSession;

    @BeforeEach
    void setUpAdmin() throws Exception {
        admin = persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");
        adminSession = login("admin@salarytontine.test");
    }

    private long register() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ndeye Diagne","email":"%s","password":"%s"}
                                """.formatted(NEW_EMAIL, NEW_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void approve(long userId, String role) throws Exception {
        mockMvc.perform(post("/api/admin/users/%d/approve".formatted(userId))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s","baseSalary":480000}
                                """.formatted(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("une inscription reste en attente et ne permet pas de se connecter")
    void registrationStaysPending() throws Exception {
        register();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(NEW_EMAIL, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("en attente de validation")));
    }

    @Test
    @DisplayName("la validation ouvre l'accès sans jamais toucher au mot de passe")
    void approvalGrantsAccessWithoutTouchingPassword() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");

        // Le mot de passe choisi à l'inscription reste le bon : personne ne l'a remplacé.
        Cookie session = login(NEW_EMAIL, NEW_PASSWORD);

        mockMvc.perform(get("/api/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.baseSalary").value(480000.00));
    }

    @Test
    @DisplayName("aucune réponse de l'API n'expose le mot de passe ni son empreinte")
    void neverExposesPassword() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");

        String listing = mockMvc.perform(get("/api/admin/users").cookie(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listing).doesNotContain(NEW_PASSWORD);
        assertThat(listing).doesNotContain("password");
        assertThat(listing).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("un refus ferme l'accès")
    void rejectionBlocksAccess() throws Exception {
        long userId = register();

        mockMvc.perform(post("/api/admin/users/%d/reject".formatted(userId)).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(NEW_EMAIL, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("refusée")));
    }

    @Test
    @DisplayName("un compte refusé perd l'accès sans attendre l'expiration de son jeton")
    void rejectionRevokesLiveSession() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");
        Cookie session = login(NEW_EMAIL, NEW_PASSWORD);

        mockMvc.perform(get("/api/users/me").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/%d/reject".formatted(userId)).cookie(adminSession))
                .andExpect(status().isOk());

        // Le jeton reste cryptographiquement valide : c'est le statut qui ferme la porte.
        mockMvc.perform(get("/api/users/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un employé change son mot de passe en fournissant l'actuel")
    void changesOwnPassword() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");
        Cookie session = login(NEW_EMAIL, NEW_PASSWORD);

        mockMvc.perform(patch("/api/users/me/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NouveauMotDePasse2"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isPresent();
        login(NEW_EMAIL, "NouveauMotDePasse2");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(NEW_EMAIL, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un mot de passe actuel erroné bloque le changement")
    void refusesWrongCurrentPassword() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");
        Cookie session = login(NEW_EMAIL, NEW_PASSWORD);

        mockMvc.perform(patch("/api/users/me/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"PasLeBon123","newPassword":"NouveauMotDePasse2"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("mot de passe actuel est incorrect")));

        // L'ancien mot de passe fonctionne toujours : rien n'a été modifié.
        login(NEW_EMAIL, NEW_PASSWORD);
    }

    @Test
    @DisplayName("l'administrateur n'a aucun moyen de fixer le mot de passe d'autrui")
    void adminCannotSetSomeoneElsePassword() throws Exception {
        long userId = register();
        approve(userId, "EMPLOYEE");

        // La création de compte avec mot de passe n'existe plus.
        mockMvc.perform(post("/api/admin/users")
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"x@salarytontine.test","password":"MotDePasse123",
                                 "role":"EMPLOYEE","baseSalary":0}
                                """))
                .andExpect(status().isMethodNotAllowed());

        // Le changement de mot de passe porte toujours sur le compte appelant.
        mockMvc.perform(patch("/api/users/me/password")
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NouveauMotDePasse2"}
                                """.formatted(TEST_PASSWORD)))
                .andExpect(status().isNoContent());

        // Celui de l'employé est intact.
        login(NEW_EMAIL, NEW_PASSWORD);
        assertThat(userRepository.findById(userId))
                .get()
                .extracting(User::getStatus)
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getId()).isNotNull();
    }
}
