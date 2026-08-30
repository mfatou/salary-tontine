package com.salarytontine.security;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Point d'accès unique a l'utilisateur authentifie.
 * L'identite provient toujours du contexte de sécurité, jamais d'un paramètre
 * fourni par le client : cela ferme la porte aux accès horizontaux (IDOR).
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AuthenticatedUser> findPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public AuthenticatedUser requirePrincipal() {
        return findPrincipal().orElseThrow(
                () -> new ResourceNotFoundException("Aucun utilisateur authentifie."));
    }

    public Long requireUserId() {
        return requirePrincipal().getId();
    }

    /** Charge l'entite complete de l'utilisateur authentifie. */
    public User requireUser() {
        Long userId = requireUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", userId));
    }

    /**
     * Auteur a tracer dans le journal d'audit, ou {@code null} lorsque l'action
     * n'est declenchee par personne : c'est le cas du planificateur mensuel,
     * qui tourne hors de toute requete HTTP.
     */
    public User findAuditAuthor() {
        return findPrincipal()
                .flatMap(principal -> userRepository.findById(principal.getId()))
                .orElse(null);
    }

    public boolean hasManagementPrivileges() {
        return findPrincipal()
                .map(principal -> principal.getRole() == Role.ACCOUNTANT || principal.getRole() == Role.ADMIN)
                .orElse(false);
    }
}
