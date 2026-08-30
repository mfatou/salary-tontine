package com.salarytontine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Parcours d'adhesion volontaire : l'employé demande, le comptable arbitre.
 *
 * <p>Le point vérifié de bout en bout est qu'une demande ne pese sur rien tant
 * qu'elle n'est pas acceptee : ni sur le nombre de participants, ni sur la
 * cagnotte, ni sur la durée du cycle.</p>
 */
@DisplayName("Integration - Demandes d'adhesion")
class JoinRequestIntegrationTest extends AbstractIntegrationTest {

    private static final String CREATE_TONTINE_BODY = """
            {"name":"Tontine Equipe A","monthlyAmount":50000,"startDate":"2026-08-01"}
            """;

    private User awa;
    private User fatou;
    private Cookie awaSession;
    private Cookie fatouSession;
    private Cookie accountantSession;

    @BeforeEach
    void setUpUsers() throws Exception {
        awa = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");
        fatou = persistEmployee("Fatou Fall", "fatou@salarytontine.test", "450000");
        persistUser("Comptable Demo", "comptable@salarytontine.test", Role.ACCOUNTANT, "0");

        awaSession = login("awa@salarytontine.test");
        fatouSession = login("fatou@salarytontine.test");
        accountantSession = login("comptable@salarytontine.test");
    }

    @Test
    @DisplayName("une demande en attente ne compte ni dans les participants ni dans la cagnotte")
    void pendingRequestDoesNotCountAsParticipation() throws Exception {
        long tontineId = createTontine();

        requestJoin(tontineId, awaSession, "Je souhaite participer.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userName").value("Awa Ndiaye"));

        mockMvc.perform(get("/api/tontines/%d".formatted(tontineId)).cookie(accountantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tontine.memberCount").value(0))
                .andExpect(jsonPath("$.tontine.potAmount").value(0))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    @DisplayName("l'acceptation crée le participant et attribue l'ordre de passage suivant")
    void acceptanceCreatesMember() throws Exception {
        long tontineId = createTontine();
        long awaRequest = requestJoinAndGetId(tontineId, awaSession);
        long fatouRequest = requestJoinAndGetId(tontineId, fatouSession);

        accept(tontineId, awaRequest).andExpect(jsonPath("$.turnOrder").value(1));
        accept(tontineId, fatouRequest).andExpect(jsonPath("$.turnOrder").value(2));

        mockMvc.perform(get("/api/tontines/%d".formatted(tontineId)).cookie(accountantSession))
                .andExpect(jsonPath("$.tontine.memberCount").value(2))
                // La cagnotte suit le nombre de participants : 50 000 x 2.
                .andExpect(jsonPath("$.tontine.potAmount").value(100000));

        assertThat(tontineMemberRepository.existsByTontineIdAndUserId(tontineId, awa.getId())).isTrue();
        assertThat(tontineMemberRepository.existsByTontineIdAndUserId(tontineId, fatou.getId())).isTrue();
    }

    @Test
    @DisplayName("un refus laisse l'employé hors de la tontine mais autorise une nouvelle demande")
    void rejectionAllowsResubmission() throws Exception {
        long tontineId = createTontine();
        long requestId = requestJoinAndGetId(tontineId, awaSession);

        mockMvc.perform(post("/api/tontines/%d/join-requests/%d/reject".formatted(tontineId, requestId))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Cycle complet pour ce trimestre."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Cycle complet pour ce trimestre."));

        assertThat(tontineMemberRepository.existsByTontineIdAndUserId(tontineId, awa.getId())).isFalse();

        requestJoin(tontineId, awaSession, "Je retente ma chance.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("liste les demandes d'une tontine une fois qu'elles ont été arbitrées")
    void listsRequestsAfterDecision() throws Exception {
        long tontineId = createTontine();
        long acceptee = requestJoinAndGetId(tontineId, awaSession);
        long refusee = requestJoinAndGetId(tontineId, fatouSession);

        accept(tontineId, acceptee);
        mockMvc.perform(post("/api/tontines/%d/join-requests/%d/reject".formatted(tontineId, refusee))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Une demande arbitrée porte un auteur de décision : sans son chargement
        // explicite, la sérialisation échouerait hors transaction.
        mockMvc.perform(get("/api/tontines/%d/join-requests".formatted(tontineId))
                        .cookie(accountantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].decidedByName").value("Comptable Demo"))
                .andExpect(jsonPath("$[1].decidedByName").value("Comptable Demo"));

        // Même exigence sur la liste vue par le demandeur.
        mockMvc.perform(get("/api/join-requests/me").cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decidedByName").value("Comptable Demo"));
    }

    @Test
    @DisplayName("une seconde demande sur la même tontine est refusée (409)")
    void rejectsDuplicateRequest() throws Exception {
        long tontineId = createTontine();
        requestJoin(tontineId, awaSession, null).andExpect(status().isCreated());

        requestJoin(tontineId, awaSession, null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("l'employé retire sa propre demande tant qu'elle est en attente (204)")
    void cancelsOwnPendingRequest() throws Exception {
        long tontineId = createTontine();
        requestJoin(tontineId, awaSession, null).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/tontines/%d/join-requests/me".formatted(tontineId))
                        .cookie(awaSession))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/join-requests/me").cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("l'activation ferme les inscriptions (400)")
    void refusesRequestOnActivatedTontine() throws Exception {
        long tontineId = createTontine();
        long awaRequest = requestJoinAndGetId(tontineId, awaSession);
        long fatouRequest = requestJoinAndGetId(tontineId, fatouSession);
        accept(tontineId, awaRequest);
        accept(tontineId, fatouRequest);

        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId)).cookie(accountantSession))
                .andExpect(status().isOk());

        User aliou = persistEmployee("Aliou Ba", "aliou@salarytontine.test", "400000");
        assertThat(aliou.getId()).isNotNull();
        Cookie aliouSession = login("aliou@salarytontine.test");

        requestJoin(tontineId, aliouSession, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("inscriptions sont closes")));
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut ni lister ni arbitrer les demandes (403)")
    void employeeCannotArbitrate() throws Exception {
        long tontineId = createTontine();
        long requestId = requestJoinAndGetId(tontineId, awaSession);

        mockMvc.perform(get("/api/tontines/%d/join-requests".formatted(tontineId)).cookie(fatouSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tontines/%d/join-requests/%d/accept".formatted(tontineId, requestId))
                        .cookie(fatouSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/join-requests/pending").cookie(fatouSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le comptable retrouve toutes les demandes en attente dans une file unique")
    void accountantSeesPendingQueue() throws Exception {
        long tontineId = createTontine();
        requestJoin(tontineId, awaSession, null).andExpect(status().isCreated());
        requestJoin(tontineId, fatouSession, null).andExpect(status().isCreated());

        mockMvc.perform(get("/api/join-requests/pending").cookie(accountantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tontineName").value("Tontine Equipe A"));
    }

    // ------------------------------------------------------------------ utilitaires

    private long createTontine() throws Exception {
        String response = mockMvc.perform(post("/api/tontines")
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TONTINE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions requestJoin(
            long tontineId, Cookie session, String motivation) throws Exception {
        String body = motivation == null
                ? "{}"
                : """
                {"motivation":"%s"}
                """.formatted(motivation);

        return mockMvc.perform(post("/api/tontines/%d/join-requests".formatted(tontineId))
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long requestJoinAndGetId(long tontineId, Cookie session) throws Exception {
        String response = requestJoin(tontineId, session, null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions accept(long tontineId, long requestId)
            throws Exception {
        return mockMvc.perform(post("/api/tontines/%d/join-requests/%d/accept"
                        .formatted(tontineId, requestId))
                        .cookie(accountantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }
}
