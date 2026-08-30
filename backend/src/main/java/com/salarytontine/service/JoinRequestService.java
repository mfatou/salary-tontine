package com.salarytontine.service;

import com.salarytontine.dto.request.AddMemberRequest;
import com.salarytontine.dto.request.JoinRequestDecision;
import com.salarytontine.dto.request.JoinTontineRequest;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineJoinRequest;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.JoinRequestStatus;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.exception.UnauthorizedOperationException;
import com.salarytontine.repository.TontineJoinRequestRepository;
import com.salarytontine.repository.TontineMemberRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adhesion volontaire a une tontine : l'employé demande, le comptable tranche.
 *
 * <p>Une demande n'entre dans le calcul de rien. Elle ne devient une
 * participation qu'a l'acceptation, qui créé alors la ligne
 * {@link TontineMember} et attribue l'ordre de passage.</p>
 */
@Service
public class JoinRequestService {

    private static final String AUDIT_ENTITY = "Tontine";
    private static final String REQUEST_RESOURCE = "Demande d'adhesion";
    private static final String CLOSED_MESSAGE =
            "Les inscriptions sont closes : seule une tontine au statut DRAFT accepte de nouvelles demandes.";

    private final TontineJoinRequestRepository joinRequestRepository;
    private final TontineMemberRepository memberRepository;
    private final TontineRepository tontineRepository;
    private final TontineService tontineService;
    private final ContributionCapacityService capacityService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public JoinRequestService(TontineJoinRequestRepository joinRequestRepository,
                              TontineMemberRepository memberRepository,
                              TontineRepository tontineRepository,
                              TontineService tontineService,
                              ContributionCapacityService capacityService,
                              CurrentUserProvider currentUserProvider,
                              AuditService auditService) {
        this.joinRequestRepository = joinRequestRepository;
        this.memberRepository = memberRepository;
        this.tontineRepository = tontineRepository;
        this.tontineService = tontineService;
        this.capacityService = capacityService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /** L'employé authentifie demande a rejoindre une tontine ouverte. */
    @Transactional
    public TontineJoinRequest request(Long tontineId, JoinTontineRequest payload) {
        Tontine tontine = tontineService.findByIdWithMembers(tontineId);
        requireOpenForEnrollment(tontine);

        User applicant = currentUserProvider.requireUser();
        String motivation = normalize(payload == null ? null : payload.motivation());

        if (memberRepository.existsByTontineIdAndUserId(tontine.getId(), applicant.getId())) {
            throw new DuplicateResourceException("Vous participez déjà a cette tontine.");
        }
        // Rien n'interdit de cumuler les tontines, tant que le total des
        // cotisations reste dans les limites du salaire.
        capacityService.requireCapacity(applicant, tontine.monthlyCost(), true);

        TontineJoinRequest saved = joinRequestRepository
                .findByTontineIdAndUserId(tontine.getId(), applicant.getId())
                .map(existing -> resubmit(existing, motivation))
                .orElseGet(() -> joinRequestRepository.save(
                        new TontineJoinRequest(tontine, applicant, motivation)));

        auditService.record(applicant, AuditAction.JOIN_REQUESTED, AUDIT_ENTITY, tontine.getId(),
                "Demande d'adhesion a '%s'".formatted(tontine.getName()));
        return saved;
    }

    /** L'employé retire sa propre demande tant qu'elle est en attente. */
    @Transactional
    public void cancelOwnRequest(Long tontineId) {
        User applicant = currentUserProvider.requireUser();
        TontineJoinRequest request = joinRequestRepository
                .findByTontineIdAndUserId(tontineId, applicant.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vous n'avez aucune demande sur cette tontine."));

        if (!request.isPending()) {
            throw new BusinessRuleException(
                    "Cette demande a déjà été traitée : elle ne peut plus être retiree.");
        }

        joinRequestRepository.delete(request);
        auditService.record(applicant, AuditAction.JOIN_REQUEST_CANCELLED, AUDIT_ENTITY, tontineId,
                "Retrait de la demande d'adhesion");
    }

    /** Demandes reçues par une tontine. Reserve au comptable et a l'administrateur. */
    @Transactional(readOnly = true)
    public List<TontineJoinRequest> findByTontine(Long tontineId) {
        Tontine tontine = tontineService.findByIdWithMembers(tontineId);
        if (!currentUserProvider.hasManagementPrivileges()) {
            throw new UnauthorizedOperationException(
                    "Seul un comptable peut consulter les demandes d'adhesion.");
        }
        return joinRequestRepository.findByTontineIdWithDetails(tontine.getId());
    }

    /**
     * File d'attente du comptable : toutes les demandes non encore arbitrees,
     * quelle que soit la tontine concernee.
     */
    @Transactional(readOnly = true)
    public List<TontineJoinRequest> findAllPending() {
        return joinRequestRepository.findAllByStatusWithDetails(JoinRequestStatus.PENDING);
    }

    /** Demandes envoyees par l'utilisateur authentifie, tous statuts confondus. */
    @Transactional(readOnly = true)
    public List<TontineJoinRequest> findMyRequests() {
        return joinRequestRepository.findByUserIdWithDetails(currentUserProvider.requireUserId());
    }

    /**
     * Accepte une demande : le demandeur devient participant et reçoit un ordre
     * de passage. Sans ordre explicite, il prend la place suivante disponible.
     */
    @Transactional
    public TontineMember accept(Long tontineId, Long requestId, JoinRequestDecision decision) {
        TontineJoinRequest request = requirePendingRequest(tontineId, requestId);
        Tontine tontine = request.getTontine();
        requireOpenForEnrollment(tontine);

        // Celui qui décide ne peut pas être celui qui en profite. Un comptable
        // participe aux tontines comme n'importe quel employé, mais accepter sa
        // propre demande lui permettrait de s'attribuer l'ordre de passage 1,
        // donc d'encaisser la cagnotte avant d'avoir cotisé.
        if (request.getUser().getId().equals(currentUserProvider.requireUserId())) {
            throw new UnauthorizedOperationException(
                    "Vous ne pouvez pas valider votre propre adhésion : un autre responsable doit "
                            + "l'arbitrer et fixer votre ordre de passage.");
        }

        if (tontine.isFull()) {
            throw new BusinessRuleException(
                    "Les %d places de cette tontine sont toutes pourvues."
                            .formatted(tontine.getTargetMemberCount()));
        }

        Integer turnOrder = (decision == null || decision.turnOrder() == null)
                ? tontineService.nextAvailableTurnOrder(tontine)
                : decision.turnOrder();

        // addMember porte toutes les règles de composition (doublon, ordre de
        // passage libre, salaire de base renseigne) : on les reutilise ici.
        TontineMember member = tontineService.addMember(
                tontine.getId(), new AddMemberRequest(request.getUser().getId(), turnOrder));

        User decider = currentUserProvider.requireUser();
        request.accept(decider);
        joinRequestRepository.save(request);

        auditService.record(decider, AuditAction.JOIN_REQUEST_ACCEPTED, AUDIT_ENTITY, tontine.getId(),
                "Demande de %s acceptee, ordre de passage %d"
                        .formatted(request.getUser().getName(), turnOrder));
        return member;
    }

    /** Refuse une demande. Le demandeur pourra en soumettre une nouvelle. */
    @Transactional
    public TontineJoinRequest reject(Long tontineId, Long requestId, JoinRequestDecision decision) {
        TontineJoinRequest request = requirePendingRequest(tontineId, requestId);

        User decider = currentUserProvider.requireUser();
        request.reject(decider, normalize(decision == null ? null : decision.note()));
        TontineJoinRequest saved = joinRequestRepository.save(request);

        auditService.record(decider, AuditAction.JOIN_REQUEST_REJECTED, AUDIT_ENTITY, tontineId,
                "Demande de %s refusée".formatted(request.getUser().getName()));
        return saved;
    }

    /** Nombre de demandes en attente, affiche au comptable comme un rappel d'action. */
    @Transactional(readOnly = true)
    public long countPending(Long tontineId) {
        return joinRequestRepository.countByTontineIdAndStatus(tontineId, JoinRequestStatus.PENDING);
    }

    /** Tontines sur lesquelles l'utilisateur a une demande encore en attente. */
    @Transactional(readOnly = true)
    public List<Long> findMyPendingTontineIds() {
        return joinRequestRepository.findTontineIdsByUserIdAndStatus(
                currentUserProvider.requireUserId(), JoinRequestStatus.PENDING);
    }

    private TontineJoinRequest resubmit(TontineJoinRequest existing, String motivation) {
        if (existing.isPending()) {
            throw new DuplicateResourceException(
                    "Votre demande est déjà en attente de reponse.");
        }
        if (existing.getStatus() == JoinRequestStatus.ACCEPTED) {
            throw new DuplicateResourceException("Vous participez déjà a cette tontine.");
        }
        existing.reopen(motivation);
        return joinRequestRepository.save(existing);
    }

    private TontineJoinRequest requirePendingRequest(Long tontineId, Long requestId) {
        TontineJoinRequest request = joinRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> ResourceNotFoundException.of(REQUEST_RESOURCE, requestId));

        if (!request.getTontine().getId().equals(tontineId)) {
            throw new ResourceNotFoundException(
                    "La demande %d ne concerne pas la tontine %d.".formatted(requestId, tontineId));
        }
        if (!request.isPending()) {
            throw new BusinessRuleException("Cette demande a déjà été traitée.");
        }
        return request;
    }

    private void requireOpenForEnrollment(Tontine tontine) {
        if (!tontine.isDraft()) {
            throw new BusinessRuleException(CLOSED_MESSAGE);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
