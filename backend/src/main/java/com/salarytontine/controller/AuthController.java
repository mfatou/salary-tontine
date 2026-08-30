package com.salarytontine.controller;

import com.salarytontine.dto.request.LoginRequest;
import com.salarytontine.dto.request.RegisterRequest;
import com.salarytontine.dto.response.UserResponse;
import com.salarytontine.entity.User;
import com.salarytontine.mapper.UserMapper;
import com.salarytontine.security.CurrentUserProvider;
import com.salarytontine.security.JwtCookieService;
import com.salarytontine.security.JwtService;
import com.salarytontine.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification par cookie JWT HttpOnly.
 * Le controleur se limite au protocole HTTP : la logique reste dans AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Inscription, connexion, deconnexion et profil courant")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtCookieService cookieService;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          JwtCookieService cookieService,
                          CurrentUserProvider currentUserProvider,
                          UserMapper userMapper) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @Operation(summary = "Inscrire un nouvel utilisateur (role EMPLOYEE impose, salaire initial a 0)")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponse(created));
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter et recevoir un cookie de session HttpOnly")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request);
        ResponseCookie cookie = cookieService.buildAuthenticationCookie(
                jwtService.generateToken(user), jwtService.getTokenLifetime().toSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(userMapper.toResponse(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Se deconnecter en invalidant le cookie de session")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.buildExpiredCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Recuperer le profil de l'utilisateur authentifie")
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(userMapper.toResponse(currentUserProvider.requireUser()));
    }
}
