package com.salarytontine.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration applicative issue exclusivement de variables d'environnement.
 * Aucun secret n'est ecrit en dur dans le code source.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Origine autorisee pour les appels CORS du frontend. */
    @NotBlank
    private String frontendUrl = "http://localhost:5173";

    private Jwt jwt = new Jwt();

    private Seed seed = new Seed();

    private Admin admin = new Admin();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    /**
     * Compte administrateur initial.
     *
     * <p>Il est créé au demarrage lorsque la base n'en contient aucun, a partir
     * de l'environnement uniquement : aucun identifiant n'est ecrit en dur, et
     * un deploiement propre part donc avec un seul compte.</p>
     */
    public static class Admin {

        /** Longueur minimale exigee pour amorcer le compte. */
        public static final int MINIMUM_PASSWORD_LENGTH = 12;

        private String name = "Administrateur";

        private String email;

        private String password;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Jwt {

        /** Longueur minimale imposee par HMAC-SHA256 ; une cle plus longue eleve l'algorithme. */
        public static final int MINIMUM_SECRET_LENGTH = 32;

        @NotBlank
        private String secret;

        @Positive
        private long expirationSeconds = 3600;

        @NotBlank
        private String cookieName = "salarytontine_token";

        private boolean cookieSecure = false;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationSeconds() {
            return expirationSeconds;
        }

        public void setExpirationSeconds(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Seed {

        private boolean enabled = false;

        /** Mot de passe des comptes de demonstration, fourni par l'environnement. */
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
