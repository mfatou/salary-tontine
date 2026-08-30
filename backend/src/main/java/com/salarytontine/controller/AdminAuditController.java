package com.salarytontine.controller;

import com.salarytontine.dto.response.AuditLogResponse;
import com.salarytontine.dto.response.PageResponse;
import com.salarytontine.entity.AuditLog;
import com.salarytontine.mapper.AuditLogMapper;
import com.salarytontine.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Administration - Audit", description = "Consultation du journal d'audit (ADMIN)")
public class AdminAuditController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;

    public AdminAuditController(AuditService auditService, AuditLogMapper auditLogMapper) {
        this.auditService = auditService;
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping
    @Operation(summary = "Lister les traces d'audit, de la plus recente a la plus ancienne")
    public ResponseEntity<PageResponse<AuditLogResponse>> listAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        Page<AuditLog> auditLogs = auditService.findRecent(page, size);
        return ResponseEntity.ok(
                PageResponse.from(auditLogs, auditLogMapper.toResponses(auditLogs.getContent())));
    }
}
