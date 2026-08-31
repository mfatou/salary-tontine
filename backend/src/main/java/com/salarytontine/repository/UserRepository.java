package com.salarytontine.repository;

import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Sert à l'amorçage : un seul administrateur initial doit être créé. */
    boolean existsByRole(Role role);

    /**
     * Chargé à chaque requête authentifiée. Le compte est relu en base plutôt
     * que déduit du jeton : un compte suspendu perd l'accès aussitôt, et un rôle
     * modifié prend effet à la requête suivante sans attendre l'expiration.
     */
    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    List<User> findAllByStatusOrderByCreatedAtAsc(UserStatus status);

    List<User> findAllByOrderByNameAsc();
}
