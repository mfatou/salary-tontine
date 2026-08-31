package com.salarytontine.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression : le rôle effectif doit provenir de la base, jamais du jeton.
 *
 * <p>Le JWT porte un rôle au moment de son émission. S'il servait d'autorité,
 * une rétrogradation resterait sans effet jusqu'à l'expiration du jeton — soit
 * une heure durant laquelle un compte conserverait des privilèges qui lui ont
 * été retirés. Ces tests vérifient que ce n'est plus le cas.</p>
 */
@DisplayName("Integration - Le role effectif provient de la base, pas du jeton")
class JwtRoleRefreshIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTANT_EMAIL = "comptable@salarytontine.test";

    @Test
    @DisplayName("un ACCOUNTANT retrograde perd ses droits avec son jeton existant")
    void demotedAccountantLosesPrivilegesOnExistingToken() throws Exception {
        User comptable = persistUser("Comptable Demo", ACCOUNTANT_EMAIL, Role.ACCOUNTANT, "0");
        Cookie session = login(ACCOUNTANT_EMAIL);

        // 1. Le jeton ouvre bien l'annuaire salarial, réservé aux gestionnaires.
        mockMvc.perform(get("/api/employees").cookie(session))
                .andExpect(status().isOk());

        // 2. Le rôle est modifié en base, sans que le jeton ne change.
        comptable.setRole(Role.EMPLOYEE);
        userRepository.saveAndFlush(comptable);

        // 3. Le MÊME cookie ne doit plus donner accès à l'annuaire.
        mockMvc.perform(get("/api/employees").cookie(session))
                .andExpect(status().isForbidden());

        // 4. Le jeton reste valide : seul le rôle a changé, la session n'est pas
        //    invalidée. Une route ouverte à tout employé répond normalement.
        mockMvc.perform(get("/api/salaries/me").cookie(session))
                .andExpect(status().isOk());

        // 5. Le profil renvoyé reflète le rôle courant.
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("une promotion prend effet immediatement sur le jeton existant")
    void promotedEmployeeGainsPrivilegesOnExistingToken() throws Exception {
        User employe = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");
        Cookie session = login("awa@salarytontine.test");

        mockMvc.perform(get("/api/employees").cookie(session))
                .andExpect(status().isForbidden());

        employe.setRole(Role.ACCOUNTANT);
        userRepository.saveAndFlush(employe);

        mockMvc.perform(get("/api/employees").cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un compte desactive perd l'acces malgre un jeton valide")
    void suspendedAccountLosesAccess() throws Exception {
        User employe = persistEmployee("Fatou Fall", "fatou@salarytontine.test", "450000");
        Cookie session = login("fatou@salarytontine.test");

        mockMvc.perform(get("/api/salaries/me").cookie(session))
                .andExpect(status().isOk());

        employe.setStatus(com.salarytontine.enums.UserStatus.REJECTED);
        userRepository.saveAndFlush(employe);

        mockMvc.perform(get("/api/salaries/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }
}
