package com.salarytontine.repository;

import com.salarytontine.entity.SalaryRecord;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

    @Query("""
            select s from SalaryRecord s
            join fetch s.user
            join fetch s.tontine
            where s.user.id = :userId
            order by s.salaryMonth desc, s.periodIndex desc
            """)
    List<SalaryRecord> findByUserIdWithDetails(@Param("userId") Long userId);

    @Query("""
            select s from SalaryRecord s
            join fetch s.user
            join fetch s.tontine
            where s.user.id = :userId and s.salaryMonth = :month
            """)
    List<SalaryRecord> findByUserIdAndSalaryMonth(@Param("userId") Long userId,
                                                  @Param("month") YearMonth month);

    /** Lignes du mois pour un employé, sans jointure : sert au recalcul du total. */
    List<SalaryRecord> findByUserIdAndSalaryMonthOrderByIdAsc(Long userId, YearMonth salaryMonth);

    @Query("""
            select s from SalaryRecord s
            join fetch s.user
            join fetch s.tontine
            where s.tontine.id = :tontineId and s.periodIndex = :periodIndex
            order by s.id asc
            """)
    List<SalaryRecord> findByTontineIdAndPeriodIndexOrderByIdAsc(@Param("tontineId") Long tontineId,
                                                                 @Param("periodIndex") Integer periodIndex);

    boolean existsByTontineIdAndPeriodIndex(Long tontineId, Integer periodIndex);
}
