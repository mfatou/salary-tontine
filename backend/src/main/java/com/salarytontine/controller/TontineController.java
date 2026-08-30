package com.salarytontine.controller;

import com.salarytontine.dto.request.AddMemberRequest;
import com.salarytontine.dto.request.CreateTontineRequest;
import com.salarytontine.dto.request.UpdateTontineRequest;
import com.salarytontine.dto.response.ScheduleEntryResponse;
import com.salarytontine.dto.response.TontineDetailResponse;
import com.salarytontine.dto.response.TontineMemberResponse;
import com.salarytontine.dto.response.TontineResponse;
import com.salarytontine.entity.Tontine;
import com.salarytontine.mapper.TontineMapper;
import com.salarytontine.service.TontineCycleService;
import com.salarytontine.service.TontineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des tontines. La consultation est ouverte aux participants ;
 * la création et l'administration sont réservées aux roles ACCOUNTANT et ADMIN.
 */
@RestController
@RequestMapping("/api/tontines")
@Tag(name = "Tontines", description = "Création, composition et activation des tontines")
public class TontineController {

    private static final String MANAGEMENT_ROLES = "hasAnyRole('ACCOUNTANT', 'ADMIN')";

    private final TontineService tontineService;
    private final TontineMapper tontineMapper;

    public TontineController(TontineService tontineService, TontineMapper tontineMapper) {
        this.tontineService = tontineService;
        this.tontineMapper = tontineMapper;
    }

    @GetMapping
    @Operation(summary = "Lister les tontines visibles par l'utilisateur authentifie")
    public ResponseEntity<List<TontineResponse>> listTontines() {
        return ResponseEntity.ok(tontineMapper.toResponses(tontineService.findVisibleTontines()));
    }

    @GetMapping("/open")
    @Operation(summary = "Lister les tontines ouvertes aux inscriptions")
    public ResponseEntity<List<TontineResponse>> listOpenTontines() {
        return ResponseEntity.ok(tontineMapper.toResponses(tontineService.findOpenTontines()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une tontine et ses participants")
    public ResponseEntity<TontineDetailResponse> getTontine(@PathVariable Long id) {
        Tontine tontine = tontineService.findByIdWithMembers(id);
        tontineService.checkReadAccess(tontine);
        return ResponseEntity.ok(tontineMapper.toDetailResponse(tontine));
    }

    @PostMapping
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Créer une tontine au statut DRAFT")
    public ResponseEntity<TontineResponse> createTontine(@Valid @RequestBody CreateTontineRequest request) {
        Tontine created = tontineService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tontineMapper.toResponse(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Modifier une tontine encore au statut DRAFT")
    public ResponseEntity<TontineResponse> updateTontine(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateTontineRequest request) {
        return ResponseEntity.ok(tontineMapper.toResponse(tontineService.update(id, request)));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Lister les participants et leur ordre de passage")
    public ResponseEntity<List<TontineMemberResponse>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(tontineMapper.toMemberResponses(tontineService.findMembers(id)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Ajouter un participant avec son ordre de passage")
    public ResponseEntity<TontineMemberResponse> addMember(@PathVariable Long id,
                                                            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tontineMapper.toMemberResponse(tontineService.addMember(id, request)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Retirer un participant d'une tontine encore au statut DRAFT")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        tontineService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/me")
    @Operation(summary = "Quitter une tontine encore ouverte")
    public ResponseEntity<Void> leaveTontine(@PathVariable Long id) {
        tontineService.leaveTontine(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Activer une tontine et figer sa composition")
    public ResponseEntity<TontineResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(tontineMapper.toResponse(tontineService.activate(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGEMENT_ROLES)
    @Operation(summary = "Annuler une tontine : le cycle s'arrête, l'historique est conserve")
    public ResponseEntity<TontineResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(tontineMapper.toResponse(tontineService.cancel(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGEMENT_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une tontine encore ouverte (aucun historique rattache)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tontineService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "Calendrier prévisionnel : mois et bénéficiaire de chaque tour")
    public ResponseEntity<List<ScheduleEntryResponse>> getSchedule(@PathVariable Long id) {
        List<ScheduleEntryResponse> schedule = tontineService.buildSchedule(id).stream()
                .map(TontineController::toScheduleEntry)
                .toList();
        return ResponseEntity.ok(schedule);
    }

    private static ScheduleEntryResponse toScheduleEntry(TontineCycleService.CycleSlot slot) {
        return new ScheduleEntryResponse(
                slot.periodIndex(),
                slot.start(),
                slot.end(),
                slot.beneficiary().getUser().getId(),
                slot.beneficiary().getUser().getName(),
                slot.beneficiary().getTurnOrder());
    }
}
