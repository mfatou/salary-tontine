package com.salarytontine.repository;

import com.salarytontine.entity.TontineMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TontineMemberRepository extends JpaRepository<TontineMember, Long> {

    @Query("""
            select m from TontineMember m
            join fetch m.user
            where m.tontine.id = :tontineId
            order by m.turnOrder asc
            """)
    List<TontineMember> findByTontineIdWithUser(@Param("tontineId") Long tontineId);

    Optional<TontineMember> findByTontineIdAndUserId(Long tontineId, Long userId);

    Optional<TontineMember> findByTontineIdAndTurnOrder(Long tontineId, Integer turnOrder);

    boolean existsByTontineIdAndUserId(Long tontineId, Long userId);
}
