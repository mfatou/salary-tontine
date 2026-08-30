package com.salarytontine.controller;

import com.salarytontine.dto.request.PeriodRequest;
import com.salarytontine.dto.response.MonthlySalaryResponse;
import com.salarytontine.dto.response.SalaryRecordResponse;
import com.salarytontine.mapper.SalaryRecordMapper;
import com.salarytontine.service.SalaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation des salaires simules.
 * L'identite est toujours déduite du contexte de sécurité : aucun endpoint
 * n'accepte un identifiant d'utilisateur arbitraire pour les roles non ADMIN.
 */
@RestController
@Tag(name = "Salaires", description = "Salaires mensuels simules")
public class SalaryController {

    private static final String MANAGEMENT_ROLES = "hasAnyRole('ACCOUNTANT', 'ADMIN')";

    private final SalaryService salaryService;
    private final SalaryRecordMapper salaryRecordMapper;

    public SalaryController(SalaryService salaryService, SalaryRecordMapper salaryRecordMapper) {
        this.salaryService = salaryService;
        this.salaryRecordMapper = salaryRecordMapper;
    }

    @PostMapping("/api/tontines/{id}/salaries/generate")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Calculer et enregistrer les salaires simulés d'un tour")
    public ResponseEntity<List<SalaryRecordResponse>> generate(@PathVariable Long id,
                                                                @Valid @RequestBody PeriodRequest request) {
        List<SalaryRecordResponse> created = salaryRecordMapper.toResponses(
                salaryService.generateForPeriod(id, request.periodIndex()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/salaries/me")
    @Operation(summary = "Historique des salaires simules de l'utilisateur authentifie")
    public ResponseEntity<List<SalaryRecordResponse>> mySalaries() {
        return ResponseEntity.ok(salaryRecordMapper.toResponses(salaryService.findMySalaryRecords()));
    }

    @GetMapping("/api/salaries/me/{month}")
    @Operation(summary = "Bulletin simule consolide du mois, toutes tontines confondues")
    public ResponseEntity<MonthlySalaryResponse> mySalaryForMonth(
            @Parameter(description = "Mois au format YYYY-MM", example = "2026-08")
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        SalaryService.MonthlyStatement statement = salaryService.findMyMonthlyStatement(month);
        return ResponseEntity.ok(new MonthlySalaryResponse(
                statement.month(),
                statement.baseSalary(),
                statement.totalDeduction(),
                statement.totalReceived(),
                statement.finalSalary(),
                salaryRecordMapper.toResponses(statement.lines())));
    }

    @GetMapping("/api/admin/users/{id}/salaries")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historique des salaires simules d'un utilisateur (ADMIN)")
    public ResponseEntity<List<SalaryRecordResponse>> userSalaries(@PathVariable Long id) {
        return ResponseEntity.ok(
                salaryRecordMapper.toResponses(salaryService.findSalaryRecordsOfUser(id)));
    }
}
