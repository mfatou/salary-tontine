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

    /** Vérifié à chaque requête authentifiée : un compte suspendu perd l'accès aussitôt. */
    boolean existsByIdAndStatus(Long id, UserStatus status);

    List<User> findAllByStatusOrderByCreatedAtAsc(UserStatus status);

    List<User> findAllByOrderByNameAsc();
}
