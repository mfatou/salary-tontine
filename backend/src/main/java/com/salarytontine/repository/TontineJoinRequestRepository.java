package com.salarytontine.repository;

import com.salarytontine.entity.TontineJoinRequest;
import com.salarytontine.enums.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * La vue ouverte en session étant désactivée, chaque lecture exposée par l'API
 * charge explicitement tout ce que le mapper traverse : le demandeur, la
 * tontine, et l'auteur de la décision.
 *
 * <p>{@code decidedBy} se charge en jointure externe : il reste nul tant que la
 * demande est en attente, et une jointure interne masquerait alors la ligne.</p>
 */
public interface TontineJoinRequestRepository extends JpaRepository<TontineJoinRequest, Long> {

    @Query("""
            select r from TontineJoinRequest r
            join fetch r.user
            left join fetch r.decidedBy
            join fetch r.tontine
            where r.tontine.id = :tontineId
            order by r.requestedAt asc
            """)
    List<TontineJoinRequest> findByTontineIdWithDetails(@Param("tontineId") Long tontineId);

    @Query("""
            select r from TontineJoinRequest r
            join fetch r.user
            left join fetch r.decidedBy
            join fetch r.tontine t
            join fetch t.createdBy
            where r.user.id = :userId
            order by r.requestedAt desc
            """)
    List<TontineJoinRequest> findByUserIdWithDetails(@Param("userId") Long userId);

    /** Toutes les demandes en attente, tous tontines confondues : la file du comptable. */
    @Query("""
            select r from TontineJoinRequest r
            join fetch r.user
            left join fetch r.decidedBy
            join fetch r.tontine t
            join fetch t.createdBy
            where r.status = :status
            order by r.requestedAt asc
            """)
    List<TontineJoinRequest> findAllByStatusWithDetails(@Param("status") JoinRequestStatus status);

    /**
     * Charge une demande avec tout ce que le mapper traverse.
     * La vue ouverte en session etant desactivee, un simple findById renverrait
     * un proxy de tontine impossible a initialiser hors transaction.
     */
    @Query("""
            select r from TontineJoinRequest r
            join fetch r.user
            left join fetch r.decidedBy
            join fetch r.tontine t
            join fetch t.createdBy
            where r.id = :id
            """)
    Optional<TontineJoinRequest> findByIdWithDetails(@Param("id") Long id);

    Optional<TontineJoinRequest> findByTontineIdAndUserId(Long tontineId, Long userId);

    long countByTontineIdAndStatus(Long tontineId, JoinRequestStatus status);

    /** Demandes en attente de l'utilisateur, tous statuts de tontine confondus. */
    @Query("""
            select r.tontine.id from TontineJoinRequest r
            where r.user.id = :userId and r.status = :status
            """)
    List<Long> findTontineIdsByUserIdAndStatus(@Param("userId") Long userId,
                                               @Param("status") JoinRequestStatus status);
}
