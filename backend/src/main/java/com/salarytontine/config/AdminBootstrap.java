package com.salarytontine.config;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le compte administrateur initial depuis l'environnement, si la base n'en
 * contient aucun. Sans lui une base vierge serait inaccessible : l'inscription
 * publique ne crée que des EMPLOYEE, et attribuer un rôle exige d'être ADMIN.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /**
     * S'execute avant le seed de demonstration, afin que celui-ci constate une
     * base déjà peuplee et s'abstienne quand les deux sont actifs.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner bootstrapAdmin(AppProperties properties,
                                            UserRepository userRepository,
                                            PasswordEncoder passwordEncoder) {
        return args -> createAdminIfMissing(properties, userRepository, passwordEncoder);
    }

    @Transactional
    void createAdminIfMissing(AppProperties properties,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {

        AppProperties.Admin admin = properties.getAdmin();
        String email = admin.getEmail();
        String password = admin.getPassword();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.debug("Amorcage administrateur ignore : APP_ADMIN_EMAIL ou APP_ADMIN_PASSWORD absent.");
            return;
        }
        if (password.length() < AppProperties.Admin.MINIMUM_PASSWORD_LENGTH) {
            log.warn("Amorcage administrateur ignore : APP_ADMIN_PASSWORD fait moins de {} caracteres.",
                    AppProperties.Admin.MINIMUM_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Amorcage administrateur ignore : un compte ADMIN existe déjà.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Amorcage administrateur ignore : l'email {} est déjà utilise par un autre compte.",
                    normalizedEmail);
            return;
        }

        userRepository.save(new User(
                admin.getName().trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                Role.ADMIN,
                BigDecimal.ZERO));

        // Le mot de passe n'est jamais journalise : seule l'adresse l'est.
        log.info("Compte administrateur initial créé pour {}.", normalizedEmail);
    }
}
