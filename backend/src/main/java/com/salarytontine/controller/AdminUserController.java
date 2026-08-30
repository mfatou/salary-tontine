package com.salarytontine.controller;

import com.salarytontine.dto.request.ApproveUserRequest;
import com.salarytontine.dto.request.UpdateRoleRequest;
import com.salarytontine.dto.request.UpdateSalaryRequest;
import com.salarytontine.dto.response.UserResponse;
import com.salarytontine.mapper.UserMapper;
import com.salarytontine.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration des comptes. Le prefixe /api/admin est déjà restreint au role
 * ADMIN par la configuration de sécurité ; @PreAuthorize rend la contrainte
 * explicite au niveau du code.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Utilisateurs", description = "Gestion des roles et des salaires fictifs (ADMIN)")
public class AdminUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public AdminUserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "Lister tous les utilisateurs")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userMapper.toResponses(userService.findAll()));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Valider une inscription et attribuer le rôle")
    public ResponseEntity<UserResponse> approve(@PathVariable Long id,
                                                @Valid @RequestBody ApproveUserRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.approve(id, request)));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Refuser une inscription")
    public ResponseEntity<UserResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.reject(id)));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Modifier le role d'un utilisateur")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.updateRole(id, request)));
    }

    @PatchMapping("/{id}/salary")
    @Operation(summary = "Definir le salaire de base fictif d'un utilisateur")
    public ResponseEntity<UserResponse> updateSalary(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateSalaryRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.updateBaseSalary(id, request)));
    }
}
