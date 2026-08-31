package com.salarytontine.service;

import com.salarytontine.dto.request.LoginRequest;
import com.salarytontine.dto.request.RegisterRequest;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.UserStatus;
import com.salarytontine.exception.UnauthorizedOperationException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription et vérification des identifiants.
 * Le role et le salaire de base sont imposes par le serveur a l'inscription :
 * un utilisateur ne peut jamais se les attribuer lui-même.
 */
@Service
public class AuthService {

    /**
     * Journal de sécurité. Les échecs d'authentification y sont tracés avec
     * l'adresse concernée et la cause, jamais avec le mot de passe soumis.
     * Ces traces restent côté serveur : la réponse HTTP, elle, demeure identique
     * dans tous les cas d'échec afin de ne rien révéler au client.
     */
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String AUDIT_ENTITY = "User";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Identifiants invalides.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("Cet email est déjà utilise.");
        }

        // L'inscription ne donne pas accès : le compte attend la validation d'un
        // administrateur. Le mot de passe reste choisi par l'employé et n'est
        // connu de personne d'autre.
        User user = new User(
                request.name().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                Role.EMPLOYEE,
                User.DEFAULT_BASE_SALARY,
                UserStatus.PENDING);

        User saved = userRepository.save(user);
        auditService.record(saved, AuditAction.USER_REGISTERED, AUDIT_ENTITY, saved.getId(),
                "Inscription de %s".formatted(saved.getEmail()));
        return saved;
    }

    /**
     * Verifie les identifiants et retourne l'utilisateur correspondant.
     * Le même message d'erreur est renvoye pour un email inconnu et un mot de
     * passe errone afin de ne pas divulguer l'existence d'un compte.
     */
    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("Échec d'authentification : aucun compte pour l'adresse {}", email);
                    return new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Échec d'authentification : mot de passe invalide pour le compte {}", email);
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        // Le statut n'est révélé qu'après vérification du mot de passe : celui
        // qui le connaît a le droit de savoir où en est son inscription.
        requireActiveAccount(user);
        return user;
    }

    private void requireActiveAccount(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
                // Rien à faire : la connexion est autorisée.
            }
            case PENDING -> {
                log.warn("Connexion refusée : le compte {} est en attente de validation", user.getEmail());
                throw new UnauthorizedOperationException(
                        "Votre inscription est en attente de validation par un administrateur.");
            }
            case REJECTED -> {
                log.warn("Connexion refusée : le compte {} a été refusé", user.getEmail());
                throw new UnauthorizedOperationException(
                        "Votre inscription a été refusée. Rapprochez-vous d'un administrateur.");
            }
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
