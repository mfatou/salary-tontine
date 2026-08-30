package com.salarytontine.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.salarytontine.integration.AbstractIntegrationTest;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Amorcage du compte administrateur.
 *
 * <p>C'est la seule porte d'entree d'une base vierge : l'inscription publique
 * ne créé que des EMPLOYEE, et il faut déjà être ADMIN pour attribuer un role.
 * Les garde-fous verifies ici evitent qu'un amorcage silencieux n'ecrase ou ne
 * duplique un compte existant.</p>
 */
@DisplayName("Integration - Amorcage de l'administrateur")
class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@salarytontine.test";
    private static final String STRONG_PASSWORD = "MotDePasseAdmin2026";

    @Autowired
    private AdminBootstrap adminBootstrap;

    private AppProperties propertiesWith(String email, String password) {
        AppProperties properties = new AppProperties();
        properties.getAdmin().setName("Administrateur");
        properties.getAdmin().setEmail(email);
        properties.getAdmin().setPassword(password);
        return properties;
    }

    private void bootstrap(String email, String password) {
        adminBootstrap.createAdminIfMissing(
                propertiesWith(email, password), userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("créé l'administrateur sur une base vierge, avec un mot de passe hache")
    void createsAdminOnEmptyDatabase() {
        bootstrap(ADMIN_EMAIL, STRONG_PASSWORD);

        Optional<User> created = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL);
        assertThat(created).isPresent();
        assertThat(created.get().getRole()).isEqualTo(Role.ADMIN);
        // Le mot de passe n'est jamais conserve en clair.
        assertThat(created.get().getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, created.get().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("normalise l'email en minuscules")
    void normalizesEmail() {
        bootstrap("  ADMIN@SalaryTontine.TEST  ", STRONG_PASSWORD);

        assertThat(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).isPresent();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("ne touche a rien lorsqu'un administrateur existe déjà")
    void skipsWhenAdminAlreadyExists() {
        User existing = persistUser("Admin Historique", "ancien@salarytontine.test", Role.ADMIN, "0");

        bootstrap(ADMIN_EMAIL, STRONG_PASSWORD);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(existing.getId())).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("refuse un mot de passe trop court plutot que d'ouvrir un compte faible")
    void skipsWhenPasswordTooShort() {
        bootstrap(ADMIN_EMAIL, "court");

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("ne fait rien lorsque l'environnement ne fournit pas d'identifiants")
    void skipsWhenNotConfigured() {
        bootstrap(null, null);
        bootstrap("", "");

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("n'ecrase pas un compte non administrateur portant le même email")
    void skipsWhenEmailBelongsToAnotherAccount() {
        persistEmployee("Homonyme", ADMIN_EMAIL, "500000");

        bootstrap(ADMIN_EMAIL, STRONG_PASSWORD);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL))
                .get()
                .extracting(User::getRole)
                .isEqualTo(Role.EMPLOYEE);
    }
}
