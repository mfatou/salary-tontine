package com.salarytontine.controller;

import com.salarytontine.dto.request.ChangePasswordRequest;
import com.salarytontine.dto.response.UserResponse;
import com.salarytontine.mapper.UserMapper;
import com.salarytontine.security.CurrentUserProvider;
import com.salarytontine.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Utilisateurs", description = "Profil et mot de passe de l'utilisateur authentifié")
public class UserController {

    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(CurrentUserProvider currentUserProvider,
                          UserService userService,
                          UserMapper userMapper) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur authentifié (aucune donnée sensible)")
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(userMapper.toResponse(currentUserProvider.requireUser()));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Changer son propre mot de passe")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request);
        return ResponseEntity.noContent().build();
    }
}
