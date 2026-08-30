package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Séparation des tâches sur les salaires.
 *
 * <p>Règle de contrôle interne : celui qui prépare la paie ne fixe pas sa
 * propre rémunération. Elle vaut pour tous les rôles — la restreindre au
 * comptable déplacerait la faille vers l'administrateur.</p>
 */
@DisplayName("Integration - Qui fixe les salaires")
class SalaryAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = """
            {"baseSalary":999000}
            """;

    private User awa;
    private User comptable;
    private User admin;
    private Cookie awaSession;
    private Cookie accountantSession;
    private Cookie adminSession;

    @BeforeEach
    void setUpUsers() throws Exception {
        awa = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");
        comptable = persistUser("Comptable Demo", "comptable@salarytontine.test",
                Role.ACCOUNTANT, "700000");
        admin = persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");

        awaSession = login("awa@salarytontine.test");
        accountantSession = login("comptable@salarytontine.test");
        adminSession = login("admin@salarytontine.test");
    }

    private org.springframework.test.web.servlet.ResultActions setSalary(long userId, Cookie session)
            throws Exception {
        return mockMvc.perform(patch("/api/employees/%d/salary".formatted(userId))
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }

    @Test
    @DisplayName("le comptable fixe le salaire d'un employé")
    void accountantSetsEmployeeSalary() throws Exception {
        setSalary(awa.getId(), accountantSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(999000.00));
    }

    @Test
    @DisplayName("le comptable ne fixe pas son propre salaire")
    void accountantCannotSetOwnSalary() throws Exception {
        setSalary(comptable.getId(), accountantSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("votre propre salaire")));

        assertThat(userRepository.findById(comptable.getId()))
                .get()
                .extracting(User::getBaseSalary)
                .isEqualTo(new BigDecimal("700000.00"));
    }

    @Test
    @DisplayName("l'administrateur non plus : la règle ne dépend pas du rôle")
    void adminCannotSetOwnSalary() throws Exception {
        setSalary(admin.getId(), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("votre propre salaire")));
    }

    @Test
    @DisplayName("l'administrateur peut fixer celui du comptable, et réciproquement")
    void crossRoleAuthorizationWorks() throws Exception {
        setSalary(comptable.getId(), adminSession).andExpect(status().isOk());

        // Chacun dépend de l'autre : c'est précisément le but de la règle.
        mockMvc.perform(patch("/api/employees/%d/salary".formatted(admin.getId()))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":0}
                                """))
                // L'administrateur n'étant pas salarié, il n'a pas de salaire à fixer.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("administrateur")));
    }

    @Test
    @DisplayName("un employé ne fixe aucun salaire, pas même le sien")
    void employeeCannotSetAnySalary() throws Exception {
        setSalary(awa.getId(), awaSession).andExpect(status().isForbidden());
        setSalary(comptable.getId(), awaSession).andExpect(status().isForbidden());
    }
}
