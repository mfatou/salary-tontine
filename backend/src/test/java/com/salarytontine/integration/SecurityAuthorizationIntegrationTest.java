package com.salarytontine.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@DisplayName("Integration - Autorisations par role")
class SecurityAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String CREATE_TONTINE_BODY = """
            {"name":"Tontine Equipe A","monthlyAmount":50000,"startDate":"2026-08-01"}
            """;

    private User employee;
    private User admin;
    private Cookie employeeSession;
    private Cookie managerSession;
    private Cookie adminSession;

    @BeforeEach
    void setUpUsers() throws Exception {
        employee = persistEmployee("Awa Ndiaye", "awa@salarytontine.test", "500000");
        persistUser("Manager Demo", "manager@salarytontine.test", Role.ACCOUNTANT, "0");
        admin = persistUser("Admin Demo", "admin@salarytontine.test", Role.ADMIN, "0");

        employeeSession = login("awa@salarytontine.test");
        managerSession = login("manager@salarytontine.test");
        adminSession = login("admin@salarytontine.test");
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas lister les utilisateurs (403)")
    void employeeCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").cookie(employeeSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas consulter les logs d'audit (403)")
    void employeeCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").cookie(employeeSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas modifier un salaire (403)")
    void employeeCannotUpdateSalary() throws Exception {
        mockMvc.perform(patch("/api/admin/users/%d/salary".formatted(employee.getId()))
                        .cookie(employeeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":9999999}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas créer de tontine (403)")
    void employeeCannotCreateTontine() throws Exception {
        mockMvc.perform(post("/api/tontines")
                        .cookie(employeeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TONTINE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un ACCOUNTANT peut créer une tontine (201)")
    void managerCanCreateTontine() throws Exception {
        mockMvc.perform(post("/api/tontines")
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TONTINE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("un ACCOUNTANT ne peut pas acceder aux endpoints ADMIN (403)")
    void managerCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users").cookie(managerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un ADMIN peut modifier un salaire (200)")
    void adminCanUpdateSalary() throws Exception {
        mockMvc.perform(patch("/api/admin/users/%d/salary".formatted(employee.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseSalary":650000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(650000.00));
    }

    @Test
    @DisplayName("un ADMIN peut modifier un role (200)")
    void adminCanUpdateRole() throws Exception {
        mockMvc.perform(patch("/api/admin/users/%d/role".formatted(employee.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ACCOUNTANT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));
    }

    @Test
    @DisplayName("un ADMIN ne peut pas se retirer son propre role (400)")
    void adminCannotDemoteSelf() throws Exception {
        mockMvc.perform(patch("/api/admin/users/%d/role".formatted(admin.getId()))
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"EMPLOYEE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un utilisateur non authentifie reçoit 401 sur les endpoints proteges")
    void anonymousReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tontines")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/salaries/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas consulter l'historique salarial d'autrui")
    void employeeCannotReadOthersSalaries() throws Exception {
        mockMvc.perform(get("/api/admin/users/%d/salaries".formatted(admin.getId()))
                        .cookie(employeeSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un EMPLOYEE peut consulter une tontine encore ouverte aux inscriptions (200)")
    void employeeCanReadOpenTontine() throws Exception {
        // Tant qu'elle est au statut DRAFT, une tontine est le catalogue des
        // inscriptions : elle doit être lisible par tout employé.
        long tontineId = createTontine();

        mockMvc.perform(get("/api/tontines/%d".formatted(tontineId)).cookie(employeeSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas consulter une tontine active dont il n'est pas membre (403)")
    void employeeCannotReadForeignActiveTontine() throws Exception {
        User fatou = persistEmployee("Fatou Fall", "fatou@salarytontine.test", "450000");
        persistEmployee("Aliou Ba", "aliou@salarytontine.test", "400000");
        Cookie outsiderSession = login("aliou@salarytontine.test");

        long tontineId = createTontine();
        addMember(tontineId, employee.getId(), 1);
        addMember(tontineId, fatou.getId(), 2);
        mockMvc.perform(post("/api/tontines/%d/activate".formatted(tontineId)).cookie(managerSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tontines/%d".formatted(tontineId)).cookie(outsiderSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un EMPLOYEE ne peut pas retirer un participant (403)")
    void employeeCannotRemoveMember() throws Exception {
        long tontineId = createTontine();

        mockMvc.perform(delete("/api/tontines/%d/members/%d".formatted(tontineId, employee.getId()))
                        .cookie(employeeSession))
                .andExpect(status().isForbidden());
    }

    private long createTontine() throws Exception {
        String response = mockMvc.perform(post("/api/tontines")
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TONTINE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addMember(long tontineId, long userId, int turnOrder) throws Exception {
        mockMvc.perform(post("/api/tontines/%d/members".formatted(tontineId))
                        .cookie(managerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"turnOrder":%d}
                                """.formatted(userId, turnOrder)))
                .andExpect(status().isCreated());
    }
}
