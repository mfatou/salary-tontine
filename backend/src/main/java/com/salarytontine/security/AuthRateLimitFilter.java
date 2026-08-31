package com.salarytontine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytontine.config.AppProperties;
import com.salarytontine.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitation de débit des points d'entrée d'authentification.
 *
 * <p>{@code /api/auth/login} et {@code /api/auth/register} sont publics et
 * traitent chacun un mot de passe avec BCrypt en coût 12, volontairement lent.
 * Sans plafond, ils exposent à la fois au bourrage d'identifiants et à une
 * saturation du serveur par tentatives concurrentes.</p>
 *
 * <p>Le compteur est une fenêtre fixe en mémoire, sans dépendance externe ni
 * infrastructure supplémentaire. Chaque clé est mise à jour par
 * {@link ConcurrentHashMap#compute}, atomique pour une clé donnée : aucun
 * verrou global, aucun état partagé non protégé.</p>
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> PROTECTED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register");

    /** Au-delà, les fenêtres échues sont purgées pour borner l'empreinte mémoire. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private static final String MESSAGE =
            "Trop de tentatives. Merci de patienter avant de réessayer.";

    private final AppProperties.RateLimit properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.properties = appProperties.getRateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        long window = Instant.now().getEpochSecond() / properties.getWindowSeconds();
        String key = clientKey(request);

        if (isWithinQuota(key, window)) {
            filterChain.doFilter(request, response);
            return;
        }

        // L'origine est tracée, jamais le corps de la requête : le mot de passe
        // soumis ne doit apparaître dans aucun journal.
        log.warn("Limite de tentatives atteinte sur {} depuis {}",
                request.getRequestURI(), request.getRemoteAddr());
        reject(request, response);
    }

    /** Le filtre ne concerne que les deux points d'entrée publics d'authentification. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    /**
     * L'adresse distante réelle sert de clé, jamais un en-tête fourni par le
     * client : {@code X-Forwarded-For} est trivialement falsifiable et
     * permettrait de contourner la limite à chaque requête. Derrière un proxy de
     * confiance, c'est à {@code server.forward-headers-strategy} de reconstituer
     * l'adresse d'origine avant que ce filtre ne s'exécute.
     */
    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr() + "|" + request.getRequestURI();
    }

    private boolean isWithinQuota(String key, long window) {
        Counter updated = counters.compute(key, (ignored, current) ->
                (current == null || current.window() != window)
                        ? new Counter(window, 1)
                        : new Counter(window, current.attempts() + 1));

        pruneIfNeeded(window);
        return updated.attempts() <= properties.getMaxAttempts();
    }

    /** Sans purge, une campagne distribuée ferait croître la table indéfiniment. */
    private void pruneIfNeeded(long currentWindow) {
        if (counters.size() > MAX_TRACKED_KEYS) {
            counters.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.getWindowSeconds()));

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                MESSAGE,
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * Remet les compteurs à zéro. Aucune route HTTP n'y donne accès : la méthode
     * n'est appelable que depuis la JVM, et sert aux tests, qui doivent rester
     * indépendants les uns des autres.
     */
    public void reset() {
        counters.clear();
    }

    /** Fenêtre courante et nombre de tentatives observées, remplacé de façon atomique. */
    private record Counter(long window, int attempts) {
    }
}
