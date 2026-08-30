package com.salarytontine.security;

import com.salarytontine.enums.UserStatus;
import com.salarytontine.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifie la requête à partir du cookie JWT.
 *
 * <p>En l'absence de jeton valide, la requête poursuit sans authentification :
 * c'est la configuration Spring Security qui décide de la rejeter ou non.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtCookieService cookieService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   JwtCookieService cookieService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieService.readToken(request)
                    .flatMap(jwtService::parseToken)
                    .ifPresent(payload -> authenticate(payload, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(JwtService.JwtPayload payload, HttpServletRequest request) {
        if (!userRepository.existsByIdAndStatus(payload.userId(), UserStatus.ACTIVE)) {
            return;
        }
        AuthenticatedUser principal =
                new AuthenticatedUser(payload.userId(), payload.email(), null, payload.role());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** Le filtre est inutile sur les routes ouvertes sans authentification. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
