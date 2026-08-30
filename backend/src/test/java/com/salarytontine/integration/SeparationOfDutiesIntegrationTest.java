package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Séparation des tâches autour de la paie et des tontines.
 *
 * <p>Deux principes tenus ici. Celui qui décide n'est pas celui qui en profite :
 * personne ne fixe son propre salaire ni ne valide sa propre adhésion. Et
 * l'administrateur gouverne sans être salarié : il n'a pas de salaire de base
 * et ne participe à aucune tontine.</p>
 */
@DisplayName("Integration - Séparation des tâches")
class SeparationOfDutiesIntegrationTest extends AbstractIntegrationTest {

    private User admin;
    private User comptable;
    private User awa;
    private Cookie adminSession;
    private Cookie comptableSession;

    @BeforeEach
    void setUpUsers() throws Exception {
        admin = persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");
        comptable = persistUser("Comptable Demo", "comptable@salarytontine.test", Role.ACCOUNTANT, "600000");
        awa = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");

        adminSession = login("admin@salarytontine.test");
        comptableSession = login("comptable@salarytontine.test");
    }

    private long createTontine(Cookie session) throws Exception {
        String response = mockMvc.perform(post("/api/tontines")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tontine Equipe A","monthlyAmount":50000,"startDate":"2026-08-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ------------------------------------------------------------------ salaires

    @Test
    @DisplayName("un comptable ne fixe pas son propre salaire de base")
    void accountantCannotSetOwnSalary() throws Exception {
        mockMvc.perform(patch("/api/employees/%d/salary".formatted(comptable.getId()))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":9999999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("votre propre salaire")));

        // isEqualByComparingTo : BigDecimal distingue 600000 de 600000.00.
        assertThat(userRepository.findById(comptable.getId()))
                .get()
                .extracting(User::getBaseSalary, org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo(comptable.getBaseSalary());
    }

    @Test
    @DisplayName("mais un administrateur peut fixer celui du comptable")
    void adminSetsAccountantSalary() throws Exception {
        mockMvc.perform(patch("/api/employees/%d/salary".formatted(comptable.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":700000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(700000.00));
    }

    @Test
    @DisplayName("un administrateur n'a pas de salaire de base")
    void adminHasNoSalary() throws Exception {
        mockMvc.perform(patch("/api/employees/%d/salary".formatted(admin.getId()))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":500000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("n'est pas salarié")));
    }

    @Test
    @DisplayName("l'annuaire salarial ne liste que les salariés")
    void payrollDirectoryExcludesAdmins() throws Exception {
        mockMvc.perform(get("/api/employees").cookie(comptableSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].role", Matchers.not(Matchers.hasItem("ADMIN"))));
    }

    // ------------------------------------------------------------------ tontines

    @Test
    @DisplayName("le comptable participe aux tontines comme tout employé")
    void accountantMayJoinTontine() throws Exception {
        long tontineId = createTontine(comptableSession);

        mockMvc.perform(post("/api/tontines/%d/join-requests".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("mais il ne valide pas sa propre adhésion")
    void accountantCannotApproveOwnJoinRequest() throws Exception {
        long tontineId = createTontine(comptableSession);

        String response = mockMvc.perform(post("/api/tontines/%d/join-requests".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(response).get("id").asLong();

        // S'auto-accepter permettrait de se donner l'ordre de passage 1, donc
        // d'encaisser la cagnotte avant d'avoir cotisé.
        mockMvc.perform(post("/api/tontines/%d/join-requests/%d/accept".formatted(tontineId, requestId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnOrder":1}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("votre propre adhésion")));

        // Un autre responsable, lui, peut trancher.
        mockMvc.perform(post("/api/tontines/%d/join-requests/%d/accept".formatted(tontineId, requestId))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("il ne s'ajoute pas non plus directement")
    void accountantCannotAddSelfAsMember() throws Exception {
        long tontineId = createTontine(comptableSession);

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(comptable.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("vous ajouter vous-même")));

        // Ajouter quelqu'un d'autre reste son travail.
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(awa.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("un administrateur ne rejoint aucune tontine")
    void adminCannotJoinTontine() throws Exception {
        long tontineId = createTontine(comptableSession);

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(admin.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("ne cotise pas")));
    }

    // ------------------------------------------------------------------ promotion

    @Test
    @DisplayName("promouvoir administrateur remet le salaire à zéro")
    void promotionToAdminClearsSalary() throws Exception {
        mockMvc.perform(patch("/api/admin/users/%d/role".formatted(awa.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.baseSalary").value(0));
    }

    @Test
    @DisplayName("mais pas tant qu'un engagement de tontine court")
    void refusesPromotionWhileEngaged() throws Exception {
        long tontineId = createTontine(comptableSession);
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(comptableSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(awa.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/admin/users/%d/role".formatted(awa.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("participe à une tontine en cours")));
    }
}
