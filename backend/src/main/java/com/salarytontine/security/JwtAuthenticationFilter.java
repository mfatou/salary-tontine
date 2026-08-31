package com.salarytontine.security;

import com.salarytontine.entity.User;
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

    /**
     * Le jeton prouve l'identité ; il ne décide pas des droits.
     *
     * <p>Le compte est relu en base à chaque requête : le rôle porté par le jeton
     * n'est jamais utilisé comme autorité effective. Sans cela, une rétrogradation
     * resterait sans effet jusqu'à l'expiration du jeton, laissant à son porteur
     * des privilèges qui lui ont été retirés.</p>
     */
    private void authenticate(JwtService.JwtPayload payload, HttpServletRequest request) {
        User user = userRepository.findByIdAndStatus(payload.userId(), UserStatus.ACTIVE).orElse(null);
        if (user == null) {
            return;
        }
        // Le hash du mot de passe reste hors du contexte de sécurité.
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getEmail(), null, user.getRole());
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
