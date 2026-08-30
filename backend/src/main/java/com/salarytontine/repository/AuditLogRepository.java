package com.salarytontine.repository;

import com.salarytontine.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            select a from AuditLog a left join fetch a.user
            """,
            countQuery = "select count(a) from AuditLog a")
    Page<AuditLog> findAllWithUser(Pageable pageable);
}
