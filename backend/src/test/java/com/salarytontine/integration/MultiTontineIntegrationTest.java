package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Participation simultanee a plusieurs tontines.
 *
 * <p>Le salaire final n'appartient pas a une tontine mais au mois : deux
 * tontines qui prelevent le même mois doivent produire un seul resultat
 * consolide, quelle que soit la ligne consultee.</p>
 */
@DisplayName("Integration - Participation a plusieurs tontines")
class MultiTontineIntegrationTest extends AbstractIntegrationTest {

    private static final String MONTH = "2026-08";
    /** Les tontines de test démarrent en août : le tour 1 couvre ce mois. */
    private static final int PERIOD = 1;

    private User awa;
    private User fatou;
    private User mamadou;
    private Cookie awaSession;
    private Cookie accountantSession;

    @BeforeEach
    void setUpUsers() throws Exception {
        awa = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");
        fatou = persistEmployee("Fatou Fall", "fatou@salarytontine.test", "450000");
        mamadou = persistEmployee("Mamadou Diop", "mamadou@salarytontine.test", "600000");
        persistUser("Comptable Demo", "comptable@salarytontine.test", Role.ACCOUNTANT, "0");

        awaSession = login("awa@salarytontine.test");
        accountantSession = login("comptable@salarytontine.test");
    }

    @Test
    @DisplayName("consolide en un seul salaire les prélèvements de deux tontines")
    void consolidatesSalaryAcrossTontines() throws Exception {
        // Awa passe en premier dans la tontine A et en second dans la tontine B :
        // en août elle encaisse la cagnotte de A seulement.
        long tontineA = createTontine("Tontine A", 50000);
        addMember(tontineA, awa.getId(), 1);
        addMember(tontineA, fatou.getId(), 2);
        activate(tontineA);

        long tontineB = createTontine("Tontine B", 30000);
        addMember(tontineB, mamadou.getId(), 1);
        addMember(tontineB, awa.getId(), 2);
        activate(tontineB);

        generateMonth(tontineA);
        generateMonth(tontineB);

        // 500 000 - 50 000 - 30 000 + 100 000 (cagnotte de A) = 520 000
        mockMvc.perform(get("/api/salaries/me/%s".formatted(MONTH)).cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(500000.00))
                .andExpect(jsonPath("$.totalDeduction").value(80000.00))
                .andExpect(jsonPath("$.totalReceived").value(100000.00))
                .andExpect(jsonPath("$.finalSalary").value(520000.00))
                .andExpect(jsonPath("$.lines.length()").value(2));

        // Chaque ligne porte le même resultat consolide : l'employé lit le
        // même salaire quelle que soit la tontine par laquelle il arrive.
        mockMvc.perform(get("/api/salaries/me").cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].finalSalary").value(520000.00))
                .andExpect(jsonPath("$[1].finalSalary").value(520000.00));
    }

    @Test
    @DisplayName("refuse une tontine dont la cotisation dépasse le salaire restant")
    void refusesTontineBeyondSalary() throws Exception {
        long tontineA = createTontine("Tontine A", 400000);
        addMember(tontineA, awa.getId(), 1);
        addMember(tontineA, fatou.getId(), 2);

        // Awa gagne 500 000 et s'est déjà engagee sur 400 000.
        long tontineB = createTontine("Tontine B", 150000);
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineB))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(awa.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("dépasserait son salaire")));
    }

    @Test
    @DisplayName("refuse une demande d'adhesion que le salaire ne supporte pas")
    void refusesJoinRequestBeyondSalary() throws Exception {
        long tontineA = createTontine("Tontine A", 450000);
        addMember(tontineA, awa.getId(), 1);
        addMember(tontineA, fatou.getId(), 2);

        long tontineB = createTontine("Tontine B", 100000);
        mockMvc.perform(post("/api/tontines/%d/join-requests".formatted(tontineB))
                        .cookie(awaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("plus que le salaire")));
    }

    @Test
    @DisplayName("refuse toute participation sans salaire de base renseigne")
    void refusesParticipationWithoutSalary() throws Exception {
        User sansSalaire = persistEmployee("Sans Salaire", "sans@salarytontine.test", "0");
        long tontine = createTontine("Tontine A", 10000);

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontine))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(sansSalaire.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("pas de salaire de base")));
    }

    @Test
    @DisplayName("le comptable participe aux tontines comme n'importe quel employé")
    void accountantCanParticipate() throws Exception {
        // Le comptable exerce une fonction en plus de son emploi : rien ne
        // justifie de l'exclure des tontines.
        User comptable = userRepository.findByEmailIgnoreCase("comptable@salarytontine.test")
                .orElseThrow();
        comptable.setBaseSalary(new java.math.BigDecimal("700000"));
        userRepository.save(comptable);

        long tontineId = createTontine("Tontine A", 50000);
        // Le comptable ne s'inscrit pas lui-même : un administrateur arbitre,
        // sinon il pourrait se choisir l'ordre de passage 1.
        User admin = persistUser("Admin Arbitre", "arbitre@salarytontine.test", Role.ADMIN, "0");
        assertThat(admin.getId()).isNotNull();
        addMemberAs(login("arbitre@salarytontine.test"), tontineId, comptable.getId(), 1);
        addMember(tontineId, awa.getId(), 2);

        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId))
                        .cookie(accountantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @DisplayName("l'administrateur ne participe pas : il n'est pas salarié")
    void adminCannotParticipate() throws Exception {
        User admin = persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");
        long tontineId = createTontine("Tontine A", 10000);

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(admin.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("administrateur")));
    }

    // ------------------------------------------------------------------ utilitaires

    private long createTontine(String name, int monthlyAmount) throws Exception {
        String response = mockMvc.perform(post("/api/tontines")
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","monthlyAmount":%d,"startDate":"2026-08-01"}
                                """.formatted(name, monthlyAmount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addMember(long tontineId, long userId, int turnOrder) throws Exception {
        addMemberAs(accountantSession, tontineId, userId, turnOrder);
    }

    private void addMemberAs(Cookie session, long tontineId, long userId, int turnOrder)
            throws Exception {
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":%d}
                                """.formatted(userId, turnOrder)))
                .andExpect(status().isCreated());
    }

    private void activate(long tontineId) throws Exception {
        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId)).cookie(accountantSession))
                .andExpect(status().isOk());
    }

    private void generateMonth(long tontineId) throws Exception {
        String body = """
                {"periodIndex":%d}
                """.formatted(PERIOD);

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
