package com.salarytontine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.salarytontine.config.AppProperties;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String VALID_SECRET = "un-secret-de-test-suffisamment-long-pour-hs256";
    private static final String OTHER_SECRET = "un-autre-secret-de-test-tout-aussi-long-pour-hs256";

    @Test
    @DisplayName("refuse de demarrer si le secret est absent")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtService(properties(null, 3600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("refuse de demarrer si le secret est trop court")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(properties("trop-court", 3600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trop court");
    }

    @Test
    @DisplayName("emet un jeton relisant l'identifiant, l'email et le role")
    void generatesReadableToken() {
        JwtService service = new JwtService(properties(VALID_SECRET, 3600));
        User user = userWithId(7L, Role.ACCOUNTANT);

        String token = service.generateToken(user);
        Optional<JwtService.JwtPayload> payload = service.parseToken(token);

        assertThat(payload).isPresent();
        assertThat(payload.get().userId()).isEqualTo(7L);
        assertThat(payload.get().email()).isEqualTo("accountant@example.test");
        assertThat(payload.get().role()).isEqualTo(Role.ACCOUNTANT);
    }

    @Test
    @DisplayName("rejette un jeton signe avec une autre cle")
    void rejectsTokenSignedWithAnotherKey() {
        String forged = new JwtService(properties(OTHER_SECRET, 3600))
                .generateToken(userWithId(7L, Role.ADMIN));

        Optional<JwtService.JwtPayload> payload =
                new JwtService(properties(VALID_SECRET, 3600)).parseToken(forged);

        assertThat(payload).isEmpty();
    }

    @Test
    @DisplayName("rejette un jeton expire")
    void rejectsExpiredToken() throws InterruptedException {
        JwtService service = new JwtService(properties(VALID_SECRET, 1));
        String token = service.generateToken(userWithId(1L, Role.EMPLOYEE));

        Thread.sleep(1_500);

        assertThat(service.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("rejette une chaine qui n'est pas un jeton")
    void rejectsGarbage() {
        JwtService service = new JwtService(properties(VALID_SECRET, 3600));

        assertThat(service.parseToken("pas-un-jwt")).isEmpty();
        assertThat(service.parseToken("")).isEmpty();
    }

    private AppProperties properties(String secret, long expirationSeconds) {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setExpirationSeconds(expirationSeconds);
        return properties;
    }

    /** L'identifiant est généré par la base : il est injecte par reflexion pour le test. */
    private User userWithId(Long id, Role role) {
        User user = new User("Test User", role.name().toLowerCase() + "@example.test",
                "$2a$10$hash", role, BigDecimal.ZERO);
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return user;
    }
}
