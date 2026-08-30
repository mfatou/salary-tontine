package com.salarytontine.security;

import com.salarytontine.config.AppProperties;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emission et vérification des jetons JWT signes en HMAC-SHA.
 * L'algorithme exact (HS256, HS384 ou HS512) est deduit de la longueur de la cle.
 * La cle provient exclusivement de la configuration d'environnement.
 */
@Service
public class JwtService {

    static final String CLAIM_USER_ID = "uid";
    static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration tokenLifetime;

    public JwtService(AppProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.length() < AppProperties.Jwt.MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET est absent ou trop court : au moins "
                            + AppProperties.Jwt.MINIMUM_SECRET_LENGTH + " caracteres sont requis.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenLifetime = Duration.ofSeconds(properties.getJwt().getExpirationSeconds());
    }

    public String generateToken(User user) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(tokenLifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Retourne le contenu du jeton s'il est authentique et non expire,
     * sinon un Optional vide. Aucune exception n'est propagee au filtre.
     */
    public Optional<JwtPayload> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get(CLAIM_USER_ID, Number.class).longValue();
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            return Optional.of(new JwtPayload(userId, claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public Duration getTokenLifetime() {
        return tokenLifetime;
    }

    /** Contenu utile d'un jeton vérifié. */
    public record JwtPayload(Long userId, String email, Role role) {
    }
}
