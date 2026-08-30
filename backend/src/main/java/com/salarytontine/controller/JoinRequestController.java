package com.salarytontine.controller;

import com.salarytontine.dto.request.JoinRequestDecision;
import com.salarytontine.dto.request.JoinTontineRequest;
import com.salarytontine.dto.response.JoinRequestResponse;
import com.salarytontine.dto.response.TontineMemberResponse;
import com.salarytontine.mapper.JoinRequestMapper;
import com.salarytontine.mapper.TontineMapper;
import com.salarytontine.service.JoinRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adhesion volontaire a une tontine.
 *
 * <p>L'employé demande a rejoindre une tontine ouverte ; le comptable accepte
 * ou refusé. L'identite du demandeur provient toujours du jeton, jamais du
 * corps de la requete.</p>
 */
@RestController
@Tag(name = "Adhesions", description = "Demandes des employés pour rejoindre une tontine")
public class JoinRequestController {

    private static final String MANAGEMENT_ROLES = "hasAnyRole('ACCOUNTANT', 'ADMIN')";

    private final JoinRequestService joinRequestService;
    private final JoinRequestMapper joinRequestMapper;
    private final TontineMapper tontineMapper;

    public JoinRequestController(JoinRequestService joinRequestService,
                                 JoinRequestMapper joinRequestMapper,
                                 TontineMapper tontineMapper) {
        this.joinRequestService = joinRequestService;
        this.joinRequestMapper = joinRequestMapper;
        this.tontineMapper = tontineMapper;
    }

    @PostMapping("/api/tontines/{tontineId}/join-requests")
    @Operation(summary = "Demander a rejoindre une tontine ouverte aux inscriptions")
    public ResponseEntity<JoinRequestResponse> request(
            @PathVariable Long tontineId,
            @Valid @RequestBody(required = false) JoinTontineRequest payload) {
        JoinRequestResponse body =
                joinRequestMapper.toResponse(joinRequestService.request(tontineId, payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/api/tontines/{tontineId}/join-requests/me")
    @Operation(summary = "Retirer sa propre demande tant qu'elle est en attente")
    public ResponseEntity<Void> cancelOwnRequest(@PathVariable Long tontineId) {
        joinRequestService.cancelOwnRequest(tontineId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/tontines/{tontineId}/join-requests")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Lister les demandes reçues par une tontine")
    public ResponseEntity<List<JoinRequestResponse>> listByTontine(@PathVariable Long tontineId) {
        return ResponseEntity.ok(
                joinRequestMapper.toResponses(joinRequestService.findByTontine(tontineId)));
    }

    @GetMapping("/api/join-requests/pending")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Lister toutes les demandes en attente d'arbitrage")
    public ResponseEntity<List<JoinRequestResponse>> listPending() {
        return ResponseEntity.ok(
                joinRequestMapper.toResponses(joinRequestService.findAllPending()));
    }

    @GetMapping("/api/join-requests/me")
    @Operation(summary = "Lister ses propres demandes d'adhesion")
    public ResponseEntity<List<JoinRequestResponse>> listMine() {
        return ResponseEntity.ok(
                joinRequestMapper.toResponses(joinRequestService.findMyRequests()));
    }

    @PostMapping("/api/tontines/{tontineId}/join-requests/{requestId}/accept")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Accepter une demande : le demandeur devient participant")
    public ResponseEntity<TontineMemberResponse> accept(
            @PathVariable Long tontineId,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) JoinRequestDecision decision) {
        TontineMemberResponse body = tontineMapper.toMemberResponse(
                joinRequestService.accept(tontineId, requestId, decision));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/api/tontines/{tontineId}/join-requests/{requestId}/reject")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Refuser une demande d'adhesion")
    public ResponseEntity<JoinRequestResponse> reject(
            @PathVariable Long tontineId,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) JoinRequestDecision decision) {
        return ResponseEntity.ok(joinRequestMapper.toResponse(
                joinRequestService.reject(tontineId, requestId, decision)));
    }
}
