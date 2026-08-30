package com.salarytontine.service;

import com.salarytontine.entity.Contribution;
import com.salarytontine.entity.Tontine;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Génération et consultation des cotisations mensuelles.
 *
 * <p>La génération est idempotente au sens metier : une seconde tentative pour
 * le même mois est refusée explicitement plutot que de créer des doublons.
 * La contrainte d'unicite (tontine, utilisateur, mois) en base constitue le
 * dernier rempart en cas d'appels concurrents.</p>
 */
@Service
public class ContributionService {

    private static final String AUDIT_ENTITY = "Tontine";

    private final ContributionRepository contributionRepository;
    private final TontineService tontineService;
    private final TontineCycleService cycleService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public ContributionService(ContributionRepository contributionRepository,
                               TontineService tontineService,
                               TontineCycleService cycleService,
                               CurrentUserProvider currentUserProvider,
                               AuditService auditService) {
        this.contributionRepository = contributionRepository;
        this.tontineService = tontineService;
        this.cycleService = cycleService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /**
     * Créé une cotisation par participant pour le mois demande.
     * Le montant provient toujours de la tontine, jamais du client.
     */
    @Transactional
    public List<Contribution> generateForPeriod(Long tontineId, int periodIndex) {
        Tontine tontine = tontineService.findByIdWithMembers(tontineId);
        requireActive(tontine);
        cycleService.requireWithinCycle(tontine, periodIndex);

        if (contributionRepository.existsByTontineIdAndPeriodIndex(tontine.getId(), periodIndex)) {
            throw new DuplicateResourceException(
                    "Les cotisations du tour %d ont déjà été générées pour cette tontine."
                            .formatted(periodIndex));
        }

        LocalDate periodStart = tontine.periodStart(periodIndex);
        List<Contribution> contributions = tontine.getMembers().stream()
                .map(member -> new Contribution(tontine, member.getUser(),
                        tontine.getMonthlyAmount(), periodIndex, periodStart))
                .toList();

        List<Contribution> saved = contributionRepository.saveAll(contributions);
        auditService.record(currentUserProvider.findAuditAuthor(), AuditAction.CONTRIBUTIONS_GENERATED,
                AUDIT_ENTITY, tontine.getId(),
                "Génération de %d cotisations de %s pour le tour %d du %s"
                        .formatted(saved.size(), tontine.getMonthlyAmount(), periodIndex, periodStart));
        return saved;
    }

    /** Cotisations visibles par l'utilisateur : toutes pour un gestionnaire, les siennes sinon. */
    @Transactional(readOnly = true)
    public List<Contribution> findByTontine(Long tontineId, Integer periodIndex) {
        Tontine tontine = tontineService.findByIdWithMembers(tontineId);
        tontineService.checkReadAccess(tontine);

        List<Contribution> contributions = (periodIndex == null)
                ? contributionRepository.findByTontineIdWithDetails(tontine.getId())
                : contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(tontine.getId(), periodIndex);

        if (currentUserProvider.hasManagementPrivileges()) {
            return contributions;
        }
        Long userId = currentUserProvider.requireUserId();
        return contributions.stream()
                .filter(contribution -> contribution.getUser().getId().equals(userId))
                .toList();
    }

    private void requireActive(Tontine tontine) {
        if (!tontine.isActive()) {
            throw new BusinessRuleException(
                    "Seule une tontine ACTIVE peut générer des cotisations (statut actuel : %s)."
                            .formatted(tontine.getStatus()));
        }
    }
}
