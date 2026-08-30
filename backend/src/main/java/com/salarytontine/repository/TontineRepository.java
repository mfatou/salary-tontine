package com.salarytontine.repository;

import com.salarytontine.entity.Tontine;
import com.salarytontine.enums.TontineStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Les requetes de lecture chargent explicitement les associations exposees par
 * l'API. La vue ouverte en session est desactivee : tout ce que le mapper
 * traverse doit donc être recupere dans la transaction.
 */
public interface TontineRepository extends JpaRepository<Tontine, Long> {

    @Query("""
            select distinct t from Tontine t
            join fetch t.createdBy
            left join fetch t.members m
            left join fetch m.user
            order by t.createdAt desc
            """)
    List<Tontine> findAllWithDetails();

    @Query("""
            select distinct t from Tontine t
            join fetch t.createdBy
            left join fetch t.members m
            left join fetch m.user
            where exists (select 1 from TontineMember tm where tm.tontine = t and tm.user.id = :userId)
            order by t.createdAt desc
            """)
    List<Tontine> findAllByMemberUserId(@Param("userId") Long userId);

    @Query("""
            select distinct t from Tontine t
            join fetch t.createdBy
            left join fetch t.members m
            left join fetch m.user
            where t.status = :status
            order by t.createdAt desc
            """)
    List<Tontine> findAllByStatusWithDetails(@Param("status") TontineStatus status);

    @Query("""
            select t from Tontine t
            join fetch t.createdBy
            left join fetch t.members m
            left join fetch m.user
            where t.id = :id
            """)
    Optional<Tontine> findByIdWithMembers(@Param("id") Long id);

    @Query("""
            select case when count(t) > 0 then true else false end
            from Tontine t join t.members m
            where m.user.id = :userId and t.status = :status
            """)
    boolean existsByMemberUserIdAndStatus(@Param("userId") Long userId, @Param("status") TontineStatus status);

    @Query("""
            select t.id from Tontine t join t.members m
            where m.user.id = :userId and t.status = :status
            order by t.startDate asc
            """)
    List<Long> findIdsByMemberUserIdAndStatus(@Param("userId") Long userId,
                                              @Param("status") TontineStatus status);

    /**
     * Tontines sur lesquelles un employé est déjà engagé.
     *
     * <p>Le total se calcule en Java plutôt qu'en SQL : le coût mensuel dépend
     * de la cadence, que la base ne sait pas convertir.</p>
     */
    @Query("""
            select t from Tontine t
            join t.members m
            where m.user.id = :userId and t.status in :statuses
            """)
    List<Tontine> findEngagedTontines(@Param("userId") Long userId,
                                      @Param("statuses") Collection<TontineStatus> statuses);

    @Query("""
            select coalesce(sum(t.monthlyAmount), 0) from Tontine t
            join t.members m
            where m.user.id = :userId and t.status in :statuses
            """)
    BigDecimal sumMonthlyCommitments(@Param("userId") Long userId,
                                     @Param("statuses") Collection<TontineStatus> statuses);
}
