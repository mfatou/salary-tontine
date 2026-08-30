package com.salarytontine.repository;

import com.salarytontine.entity.Contribution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    @Query("""
            select c from Contribution c
            join fetch c.user
            join fetch c.tontine
            where c.tontine.id = :tontineId
            order by c.periodIndex desc, c.id asc
            """)
    List<Contribution> findByTontineIdWithDetails(@Param("tontineId") Long tontineId);

    @Query("""
            select c from Contribution c
            join fetch c.user
            join fetch c.tontine
            where c.tontine.id = :tontineId and c.periodIndex = :periodIndex
            order by c.id asc
            """)
    List<Contribution> findByTontineIdAndPeriodIndexOrderByIdAsc(@Param("tontineId") Long tontineId,
                                                                 @Param("periodIndex") Integer periodIndex);

    boolean existsByTontineIdAndPeriodIndex(Long tontineId, Integer periodIndex);
}
