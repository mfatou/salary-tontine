package com.salarytontine.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.salarytontine.entity.User;
import com.salarytontine.enums.ContributionStatus;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.TontineStatus;
import jakarta.servlet.http.Cookie;
import java.time.YearMonth;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Parcours complet : création, composition, activation, cotisations et salaires,
 * execute contre une base PostgreSQL réelle.
 */
@DisplayName("Integration - Cycle de vie complet d'une tontine")
class TontineLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    /** La tontine démarre en août : le tour 1 couvre ce mois, le tour 2 septembre. */
    private static final int FIRST_PERIOD = 1;
    private static final int SECOND_PERIOD = 2;
    private static final List<String> EMPLOYEE_NAMES =
            List.of("Awa Ndiaye", "Fatou Fall", "Mamadou Diop", "Khady Sarr", "Aliou Ba");

    private Cookie managerSession;
    private List<User> employees;
    private long tontineId;

    @BeforeEach
    void setUpScenario() throws Exception {
        persistUser("Manager Demo", "manager@salarytontine.test", Role.ACCOUNTANT, "0");
        managerSession = login("manager@salarytontine.test");

        employees = EMPLOYEE_NAMES.stream()
                .map(name -> persistEmployee(name,
                        name.split(" ")[0].toLowerCase() + "@salarytontine.test", "500000"))
                .toList();

        tontineId = createTontine();
    }

    private long createTontine() throws Exception {
        String response = mockMvc.perform(post("/api/tontines")
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tontine Equipe A","monthlyAmount":50000,"startDate":"2026-08-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addAllMembers() throws Exception {
        for (int index = 0; index < employees.size(); index++) {
            mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                            .cookie(managerSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":%d,"turnOrder":%d}
                                    """.formatted(employees.get(index).getId(), index + 1)))
                    .andExpect(status().isCreated());
        }
    }

    private void activate() throws Exception {
        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId)).cookie(managerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.potAmount").value(250000.00));
    }

    private void generateContributions(int periodIndex) throws Exception {
        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":%d}
                                """.formatted(periodIndex)))
                .andExpect(status().isCreated());
    }

    private void generateSalaries(int periodIndex) throws Exception {
        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":%d}
                                """.formatted(periodIndex)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("refusé l'activation tant qu'il n'y a pas assez de participants")
    void rejectsActivationWithoutEnoughMembers() throws Exception {
        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId)).cookie(managerSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("au moins 2")));
    }

    @Test
    @DisplayName("refuse un ordre de passage déjà attribue (409)")
    void rejectsDuplicateTurnOrder() throws Exception {
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(employees.get(0).getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(employees.get(1).getId())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("refuse un participant déjà inscrit dans la tontine (409)")
    void rejectsDuplicateMember() throws Exception {
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":1}
                                """.formatted(employees.get(0).getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":2}
                                """.formatted(employees.get(0).getId())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("refuse un montant mensuel negatif ou nul (400)")
    void rejectsNonPositiveMonthlyAmount() throws Exception {
        mockMvc.perform(post("/api/tontines")
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tontine invalide","monthlyAmount":-50000,"startDate":"2026-08-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.monthlyAmount").exists());

        mockMvc.perform(post("/api/tontines")
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tontine invalide","monthlyAmount":0,"startDate":"2026-08-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("expose le calendrier prévisionnel du cycle")
    void exposesSchedule() throws Exception {
        addAllMembers();
        activate();

        mockMvc.perform(get("/api/tontines/%d/schedule".formatted(tontineId)).cookie(managerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].periodIndex").value(1))
                .andExpect(jsonPath("$[0].start").value("2026-08-01"))
                .andExpect(jsonPath("$[0].end").value("2026-08-31"))
                .andExpect(jsonPath("$[0].beneficiaryName").value("Awa Ndiaye"))
                .andExpect(jsonPath("$[1].periodIndex").value(2))
                .andExpect(jsonPath("$[1].start").value("2026-09-01"))
                .andExpect(jsonPath("$[1].beneficiaryName").value("Fatou Fall"))
                .andExpect(jsonPath("$[4].periodIndex").value(5))
                .andExpect(jsonPath("$[4].start").value("2026-12-01"))
                .andExpect(jsonPath("$[4].beneficiaryName").value("Aliou Ba"));
    }

    @Test
    @DisplayName("génère une cotisation par participant et refuse le doublon (409)")
    void generatesContributionsOncePerMonth() throws Exception {
        addAllMembers();
        activate();

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].amount").value(50000.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":1}
                                """))
                .andExpect(status().isConflict());

        assertThat(contributionRepository
                .findByTontineIdAndPeriodIndexOrderByIdAsc(tontineId, FIRST_PERIOD)).hasSize(5);
    }

    @Test
    @DisplayName("refuse un mois hors cycle (400)")
    void rejectsMonthOutsideCycle() throws Exception {
        addAllMembers();
        activate();

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":0}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":6}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuse un mois au format invalide (400)")
    void rejectsMalformedMonth() throws Exception {
        addAllMembers();
        activate();

        mockMvc.perform(post("/api/tontines/%d/contributions/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":"pas-un-nombre"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuse la génération des salaires avant celle des cotisations (400)")
    void rejectsSalariesBeforeContributions() throws Exception {
        addAllMembers();
        activate();

        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("cotisations")));
    }

    @Test
    @DisplayName("calcule les salaires d'août : Awa bénéficiaire a 700000, les autres a 450000")
    void calculatesAugustSalaries() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);

        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.userName == 'Awa Ndiaye')].finalSalary").value(700000.00))
                .andExpect(jsonPath("$[?(@.userName == 'Awa Ndiaye')].tontineReceived").value(250000.00))
                .andExpect(jsonPath("$[?(@.userName == 'Awa Ndiaye')].tontineDeduction").value(50000.00))
                .andExpect(jsonPath("$[?(@.userName == 'Fatou Fall')].finalSalary").value(450000.00))
                .andExpect(jsonPath("$[?(@.userName == 'Aliou Ba')].finalSalary").value(450000.00));

        assertThat(contributionRepository
                .findByTontineIdAndPeriodIndexOrderByIdAsc(tontineId, FIRST_PERIOD))
                .allMatch(contribution -> contribution.getStatus() == ContributionStatus.DEDUCTED);
    }

    @Test
    @DisplayName("le bénéficiaire change en septembre : Fatou passe a 700000")
    void beneficiaryChangesInSeptember() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);
        generateContributions(SECOND_PERIOD);

        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[?(@.userName == 'Fatou Fall')].finalSalary").value(700000.00))
                .andExpect(jsonPath("$[?(@.userName == 'Awa Ndiaye')].finalSalary").value(450000.00));
    }

    @Test
    @DisplayName("refuse une double génération des salaires (409)")
    void rejectsDuplicateSalaryGeneration() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);

        mockMvc.perform(post("/api/tontines/%d/salaries/generate".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodIndex":1}
                                """))
                .andExpect(status().isConflict());

        assertThat(salaryRecordRepository
                .findByTontineIdAndPeriodIndexOrderByIdAsc(tontineId, FIRST_PERIOD)).hasSize(5);
    }

    @Test
    @DisplayName("passe la tontine à COMPLETED après le dernier tour du cycle")
    void completesTontineAtEndOfCycle() throws Exception {
        addAllMembers();
        activate();

        for (int periodIndex = 1; periodIndex <= EMPLOYEE_NAMES.size(); periodIndex++) {
            generateContributions(periodIndex);
            generateSalaries(periodIndex);
        }

        assertThat(tontineRepository.findById(tontineId).orElseThrow().getStatus())
                .isEqualTo(TontineStatus.COMPLETED);
    }

    @Test
    @DisplayName("un employé ne consulte que son propre salaire simule")
    void employeeSeesOnlyOwnSalary() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);

        Cookie awaSession = login("awa@salarytontine.test");

        mockMvc.perform(get("/api/salaries/me").cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userName").value("Awa Ndiaye"))
                .andExpect(jsonPath("$[0].finalSalary").value(700000.00));

        // Le mois est desormais un bulletin consolide : un employé peut cotiser
        // a plusieurs tontines, le salaire final n'a de sens qu'au niveau du mois.
        mockMvc.perform(get("/api/salaries/me/2026-08").cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.baseSalary").value(500000.00))
                .andExpect(jsonPath("$.totalDeduction").value(50000.00))
                .andExpect(jsonPath("$.totalReceived").value(250000.00))
                .andExpect(jsonPath("$.finalSalary").value(700000.00))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].beneficiary").value(true));

        mockMvc.perform(get("/api/tontines/%d/contributions?month=2026-08".formatted(tontineId))
                        .cookie(awaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userName").value("Awa Ndiaye"));
    }

    @Test
    @DisplayName("le tableau de bord expose la tontine active et le dernier salaire")
    void dashboardExposesActiveTontine() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);

        mockMvc.perform(get("/api/dashboard").cookie(login("fatou@salarytontine.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("Fatou Fall"))
                .andExpect(jsonPath("$.activeTontine.name").value("Tontine Equipe A"))
                .andExpect(jsonPath("$.myTurnOrder").value(2))
                .andExpect(jsonPath("$.myTurnDate").value("2026-09-01"))
                .andExpect(jsonPath("$.latestSalaryRecord.finalSalary").value(450000.00));
    }

    @Test
    @DisplayName("trace les actions de gestion dans le journal d'audit")
    void writesAuditTrail() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);

        persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");

        mockMvc.perform(get("/api/admin/audit-logs").cookie(login("admin@salarytontine.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'TONTINE_CREATED')]").exists())
                .andExpect(jsonPath("$.content[?(@.action == 'MEMBER_ADDED')]").exists())
                .andExpect(jsonPath("$.content[?(@.action == 'TONTINE_ACTIVATED')]").exists())
                .andExpect(jsonPath("$.content[?(@.action == 'CONTRIBUTIONS_GENERATED')]").exists())
                .andExpect(jsonPath("$.content[?(@.action == 'SALARIES_GENERATED')]").exists());
    }

    @Test
    @DisplayName("aucune trace d'audit ne contient de secret")
    void auditDetailsNeverContainSecrets() throws Exception {
        addAllMembers();
        activate();
        generateContributions(FIRST_PERIOD);
        generateSalaries(FIRST_PERIOD);

        assertThat(auditLogRepository.findAll())
                .isNotEmpty()
                .allSatisfy(auditLog -> {
                    String details = auditLog.getDetails();
                    assertThat(details).doesNotContain(TEST_PASSWORD);
                    assertThat(details).doesNotContain("$2a$");
                    assertThat(details).doesNotContainIgnoringCase("password");
                    assertThat(details).doesNotContainIgnoringCase("secret");
                });
    }
}
