package com.salarytontine.controller;

import com.salarytontine.dto.request.UpdateSalaryRequest;
import com.salarytontine.dto.response.SalaryRecordResponse;
import com.salarytontine.dto.response.UserResponse;
import com.salarytontine.mapper.SalaryRecordMapper;
import com.salarytontine.mapper.UserMapper;
import com.salarytontine.service.SalaryService;
import com.salarytontine.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annuaire salarial, ouvert au comptable et a l'administrateur.
 *
 * <p>C'est le comptable qui prepare les prélèvements : il lui faut donc voir le
 * salaire de base de chaque employé et pouvoir le corriger. Cette vue est
 * volontairement separee de {@code /api/admin}, réservé aux opérations sur les
 * comptes eux-mêmes (création, changement de role, journal d'audit).</p>
 */
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
@Tag(name = "Annuaire salarial", description = "Salaires de base des employés (ACCOUNTANT, ADMIN)")
public class EmployeeDirectoryController {

    private final UserService userService;
    private final SalaryService salaryService;
    private final UserMapper userMapper;
    private final SalaryRecordMapper salaryRecordMapper;

    public EmployeeDirectoryController(UserService userService,
                                       SalaryService salaryService,
                                       UserMapper userMapper,
                                       SalaryRecordMapper salaryRecordMapper) {
        this.userService = userService;
        this.salaryService = salaryService;
        this.userMapper = userMapper;
        this.salaryRecordMapper = salaryRecordMapper;
    }

    @GetMapping
    @Operation(summary = "Lister les employés avec leur salaire de base")
    public ResponseEntity<List<UserResponse>> listEmployees() {
        return ResponseEntity.ok(userMapper.toResponses(userService.findSalaried()));
    }

    @PatchMapping("/{id}/salary")
    @Operation(summary = "Definir le salaire de base fictif d'un employé")
    public ResponseEntity<UserResponse> updateSalary(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateSalaryRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.updateBaseSalary(id, request)));
    }

    @GetMapping("/{id}/salaries")
    @Operation(summary = "Historique des salaires simules d'un employé")
    public ResponseEntity<List<SalaryRecordResponse>> salaryHistory(@PathVariable Long id) {
        return ResponseEntity.ok(
                salaryRecordMapper.toResponses(salaryService.findSalaryRecordsOfUser(id)));
    }
}
