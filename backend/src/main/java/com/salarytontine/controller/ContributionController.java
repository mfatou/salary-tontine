package com.salarytontine.controller;

import com.salarytontine.dto.request.PeriodRequest;
import com.salarytontine.dto.response.ContributionResponse;
import com.salarytontine.mapper.ContributionMapper;
import com.salarytontine.service.ContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tontines/{id}/contributions")
@Tag(name = "Cotisations", description = "Génération et consultation des cotisations mensuelles")
public class ContributionController {

    private final ContributionService contributionService;
    private final ContributionMapper contributionMapper;

    public ContributionController(ContributionService contributionService,
                                  ContributionMapper contributionMapper) {
        this.contributionService = contributionService;
        this.contributionMapper = contributionMapper;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    @Operation(summary = "Générer les cotisations d'un tour pour tous les participants")
    public ResponseEntity<List<ContributionResponse>> generate(@PathVariable Long id,
                                                                @Valid @RequestBody PeriodRequest request) {
        List<ContributionResponse> created = contributionMapper.toResponses(
                contributionService.generateForPeriod(id, request.periodIndex()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Lister les cotisations d'une tontine, filtrables par tour")
    public ResponseEntity<List<ContributionResponse>> listContributions(
            @PathVariable Long id,
            @Parameter(description = "Rang du tour dans le cycle", example = "1")
            @RequestParam(required = false) Integer periodIndex) {

        return ResponseEntity.ok(
                contributionMapper.toResponses(contributionService.findByTontine(id, periodIndex)));
    }
}
