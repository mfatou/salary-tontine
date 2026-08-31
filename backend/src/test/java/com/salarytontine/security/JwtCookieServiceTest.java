package com.salarytontine.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.salarytontine.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

@DisplayName("JwtCookieService")
class JwtCookieServiceTest {

    private static final String COOKIE_NAME = "salarytontine_token";

    private AppProperties properties(boolean secure) {
        AppProperties properties = new AppProperties();
        properties.getJwt().setCookieName(COOKIE_NAME);
        properties.getJwt().setCookieSecure(secure);
        return properties;
    }

    @Test
    @DisplayName("la configuration par defaut exige un cookie Secure")
    void defaultConfigurationIsSecure() {
        assertThat(new AppProperties().getJwt().isCookieSecure())
                .as("un déploiement qui n'aurait pas renseigné JWT_COOKIE_SECURE "
                        + "doit hériter de la valeur protectrice")
                .isTrue();
    }

    @Test
    @DisplayName("porte Secure, HttpOnly et SameSite lorsque configure ainsi")
    void carriesSecureAttributeWhenConfigured() {
        ResponseCookie cookie = new JwtCookieService(properties(true))
                .buildAuthenticationCookie("jeton", 3600);

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("omet Secure uniquement lorsque la configuration le demande explicitement")
    void omitsSecureOnlyWhenExplicitlyDisabled() {
        ResponseCookie cookie = new JwtCookieService(properties(false))
                .buildAuthenticationCookie("jeton", 3600);

        assertThat(cookie.isSecure()).isFalse();
        // Les autres protections restent appliquées quoi qu'il arrive.
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("le cookie de deconnexion conserve les memes attributs")
    void expiredCookieKeepsSameAttributes() {
        ResponseCookie cookie = new JwtCookieService(properties(true)).buildExpiredCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
    }
}
